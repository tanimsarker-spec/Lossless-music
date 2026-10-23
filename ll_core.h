#ifndef LL_CORE_H
#define LL_CORE_H
#ifdef __cplusplus
extern "C" {
#endif
int  ll_open(const char* path);      /* 0 = ok */
void ll_close(void);
void ll_play(void);
void ll_pause(void);
void ll_seek(double seconds);
double ll_position(void);
double ll_duration(void);
int  ll_is_playing(void);
int  ll_finished(void);
void ll_eq(int enabled);             /* 0 = bypass (untouched samples) */
void ll_band(int index, float db);   /* 10 bands, -12..+12 dB */
void ll_preamp(float db);
unsigned ll_source_rate(void);       /* file's sample rate */
unsigned ll_source_bits(void);       /* FLAC bit depth, 0 if unknown */
unsigned ll_output_rate(void);       /* rate of the opened output stream */
#ifdef __cplusplus
}
#endif
#endif
