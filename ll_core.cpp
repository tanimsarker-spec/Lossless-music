#define _CRT_SECURE_NO_WARNINGS
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"
#include "ll_core.h"
#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <mutex>

namespace {
constexpr int NB = 10, MAXCH = 8;
const double FR[NB] = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
const double PI = 3.14159265358979323846;
struct Bq { double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0; };

std::mutex mx;
ma_decoder dec;
ma_device dev;
bool haveDec = false, haveDev = false;
std::atomic<bool> playing{false}, finished{false}, eqOn{false};
float gains[NB] = {0};
double preLin = 1.0;
Bq bq[NB];
double z[MAXCH][NB][2];
unsigned channels = 0, srcRate = 0, srcBits = 0, outRate = 0;

void calc(int i, double fs) {
  Bq q;
  if (FR[i] < fs * 0.45) {
    double A = std::pow(10.0, gains[i] / 40.0), w = 2 * PI * FR[i] / fs;
    double c = std::cos(w), s = std::sin(w), b0, b1, b2, a0, a1, a2;
    if (i == 0 || i == NB - 1) {  // shelves, slope S = 1
      double al = s / 2.0 * std::sqrt(2.0), t = 2 * std::sqrt(A) * al;
      if (i == 0) {
        b0 = A * ((A + 1) - (A - 1) * c + t);  b1 = 2 * A * ((A - 1) - (A + 1) * c);
        b2 = A * ((A + 1) - (A - 1) * c - t);  a0 = (A + 1) + (A - 1) * c + t;
        a1 = -2 * ((A - 1) + (A + 1) * c);     a2 = (A + 1) + (A - 1) * c - t;
      } else {
        b0 = A * ((A + 1) + (A - 1) * c + t);  b1 = -2 * A * ((A - 1) + (A + 1) * c);
        b2 = A * ((A + 1) + (A - 1) * c - t);  a0 = (A + 1) - (A - 1) * c + t;
        a1 = 2 * ((A - 1) - (A + 1) * c);      a2 = (A + 1) - (A - 1) * c - t;
      }
    } else {  // peaking, Q = 1.1
      double al = s / (2 * 1.1);
      b0 = 1 + al * A; b1 = -2 * c; b2 = 1 - al * A;
      a0 = 1 + al / A; a1 = -2 * c; a2 = 1 - al / A;
    }
    q.b0 = b0 / a0; q.b1 = b1 / a0; q.b2 = b2 / a0; q.a1 = a1 / a0; q.a2 = a2 / a0;
  }
  bq[i] = q;
}
void calcAll() { for (int i = 0; i < NB; i++) calc(i, outRate ? outRate : 44100); }

void cb(ma_device*, void* out, const void*, ma_uint32 n) {
  float* o = (float*)out;
  unsigned ch = channels;
  std::lock_guard<std::mutex> g(mx);
  if (!playing.load() || !haveDec) { std::memset(o, 0, (size_t)n * ch * sizeof(float)); return; }
  ma_uint64 got = 0;
  ma_decoder_read_pcm_frames(&dec, o, n, &got);
  if (got < n) {
    std::memset(o + got * ch, 0, (size_t)(n - got) * ch * sizeof(float));
    finished = true; playing = false;
  }
  if (eqOn.load()) {  // 32-bit float in, double-precision filters, clip protection
    for (ma_uint64 f = 0; f < got; f++)
      for (unsigned c = 0; c < ch && c < (unsigned)MAXCH; c++) {
        double x = o[f * ch + c] * preLin;
        for (int b = 0; b < NB; b++) {
          double y = bq[b].b0 * x + z[c][b][0];
          z[c][b][0] = bq[b].b1 * x - bq[b].a1 * y + z[c][b][1];
          z[c][b][1] = bq[b].b2 * x - bq[b].a2 * y;
          x = y;
        }
        o[f * ch + c] = (float)(x > 1.0 ? 1.0 : (x < -1.0 ? -1.0 : x));
      }
  }
}

void flacHeader(const char* p, unsigned& rate, unsigned& bits) {
  rate = bits = 0;
  FILE* f = std::fopen(p, "rb");
  if (!f) return;
  unsigned char h[42];
  size_t n = std::fread(h, 1, 42, f);
  std::fclose(f);
  if (n < 26 || std::memcmp(h, "fLaC", 4) != 0 || (h[4] & 0x7f) != 0) return;
  const unsigned char* d = h + 8;
  rate = (d[10] << 12) | (d[11] << 4) | (d[12] >> 4);
  bits = (((d[12] & 1) << 4) | (d[13] >> 4)) + 1;
}
}  // namespace

extern "C" {
void ll_close(void) {
  playing = false;
  if (haveDev) { ma_device_uninit(&dev); haveDev = false; }
  std::lock_guard<std::mutex> g(mx);
  if (haveDec) { ma_decoder_uninit(&dec); haveDec = false; }
}

int ll_open(const char* path) {
  ll_close();
  ma_decoder_config dc = ma_decoder_config_init(ma_format_f32, 0, 0);  // native rate/channels
  {
    std::lock_guard<std::mutex> g(mx);
    if (ma_decoder_init_file(path, &dc, &dec) != MA_SUCCESS) return 1;
    haveDec = true;
    channels = dec.outputChannels;
    srcRate = dec.outputSampleRate;
    unsigned r = 0; flacHeader(path, r, srcBits);
    finished = false;
  }
  ma_device_config c = ma_device_config_init(ma_device_type_playback);
  c.playback.format = ma_format_f32;
  c.playback.channels = channels;
  c.sampleRate = srcRate;  // ask the device for the file's own rate: no resampling by us
  c.dataCallback = cb;
  if (ma_device_init(NULL, &c, &dev) != MA_SUCCESS) { ll_close(); return 2; }
  haveDev = true;
  std::lock_guard<std::mutex> g(mx);
  outRate = dev.playback.internalSampleRate;
  std::memset(z, 0, sizeof(z));
  calcAll();
  return 0;
}

void ll_play(void) {
  if (!haveDev) return;
  if (finished.load()) { std::lock_guard<std::mutex> g(mx); ma_decoder_seek_to_pcm_frame(&dec, 0); finished = false; }
  playing = true;
  if (!ma_device_is_started(&dev)) ma_device_start(&dev);
}
void ll_pause(void) { playing = false; if (haveDev) ma_device_stop(&dev); }
void ll_seek(double s) {
  std::lock_guard<std::mutex> g(mx);
  if (haveDec) { ma_decoder_seek_to_pcm_frame(&dec, (ma_uint64)(s * srcRate)); finished = false; }
}
double ll_position(void) {
  std::lock_guard<std::mutex> g(mx);
  ma_uint64 c = 0;
  if (haveDec && srcRate) ma_decoder_get_cursor_in_pcm_frames(&dec, &c);
  return srcRate ? (double)c / srcRate : 0.0;
}
double ll_duration(void) {
  std::lock_guard<std::mutex> g(mx);
  ma_uint64 l = 0;
  if (haveDec && srcRate && ma_decoder_get_length_in_pcm_frames(&dec, &l) == MA_SUCCESS) return (double)l / srcRate;
  return 0.0;
}
int ll_is_playing(void) { return playing.load() ? 1 : 0; }
int ll_finished(void) { return finished.load() ? 1 : 0; }
void ll_eq(int on) {
  std::lock_guard<std::mutex> g(mx);
  if (on) std::memset(z, 0, sizeof(z));
  eqOn = on != 0;
}
void ll_band(int i, float db) {
  if (i < 0 || i >= NB) return;
  std::lock_guard<std::mutex> g(mx);
  gains[i] = db < -12 ? -12 : (db > 12 ? 12 : db);
  calc(i, outRate ? outRate : 44100);
}
void ll_preamp(float db) {
  std::lock_guard<std::mutex> g(mx);
  preLin = std::pow(10.0, db / 20.0);
}
unsigned ll_source_rate(void) { return srcRate; }
unsigned ll_source_bits(void) { return srcBits; }
unsigned ll_output_rate(void) { return outRate; }
}
