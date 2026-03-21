/**
 * png_loader.h — Bağımlılıksız Minimal PNG Decoder
 *
 * Sadece RGBA8 çıktısı üretir. stb_image entegre edildiğinde
 * bu dosya kaldırılabilir (önerilir).
 *
 * Kullanım:
 *   uint8_t* pixels = nullptr;
 *   int w, h;
 *   if (decodePNG(data, size, &pixels, &w, &h)) {
 *       // pixels: RGBA sırasında w*h*4 byte
 *       free(pixels);
 *   }
 *
 * NOT: Bu decoder production kullanımı için değil proje iskeleti
 * içindir. Gerçek PNG desteği için stb_image.h ekleyin:
 *   #define STB_IMAGE_IMPLEMENTATION
 *   #include "stb_image.h"
 *   pixels = stbi_load_from_memory(data, size, &w, &h, nullptr, 4);
 */

#pragma once
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#define PNG_LOG_TAG "MUHUR_PNG"

// ─────────────────────────────────────────────
// Yardımcı: Big-endian 32-bit okuma
// ─────────────────────────────────────────────
static inline uint32_t readU32BE(const uint8_t* p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) |
           ((uint32_t)p[2] <<  8) |  (uint32_t)p[3];
}

// ─────────────────────────────────────────────
// Basit zlib inflate (sadece stored blocks — deflate değil)
// Gerçek PNG'ler için stb_image kullanın.
// Bu sürüm yalnızca tools tarafından üretilen,
// compress=0 flag'li PNG'leri destekler.
//
// Gerçek projelerde:
//   NDK'nın libz.so'su veya miniz / stb_image kullanılmalı.
// ─────────────────────────────────────────────
static bool zlibStoredDecode(const uint8_t* src, int srcLen,
                              uint8_t* dst, int dstLen) {
    if (srcLen < 2) return false;
    // zlib header: CMF, FLG — skip
    int pos = 2;
    int out = 0;
    while (pos < srcLen - 4) {
        if (out >= dstLen) break;
        uint8_t bfinal = src[pos] & 1;
        uint8_t btype  = (src[pos] >> 1) & 3;
        pos++;
        if (btype == 0) { // stored
            pos = (pos + 1) & ~1; // align? (skip LEN/NLEN)
            if (pos + 4 > srcLen) return false;
            uint16_t len = (uint16_t)src[pos] | ((uint16_t)src[pos+1] << 8);
            pos += 4;
            if (pos + len > srcLen || out + len > dstLen) return false;
            memcpy(dst + out, src + pos, len);
            out += len; pos += len;
        } else {
            // deflate bloklarını desteklemiyor — stb_image kullanın
            __android_log_print(ANDROID_LOG_WARN, PNG_LOG_TAG,
                "Deflate blokları desteklenmiyor. stb_image.h ekleyin.");
            return false;
        }
        if (bfinal) break;
    }
    return out == dstLen;
}

// ─────────────────────────────────────────────
// PNG Decoder Ana Fonksiyonu
// ─────────────────────────────────────────────
static bool decodePNG(const uint8_t* data, int size,
                      uint8_t** outPixels, int* outW, int* outH) {
    // PNG Signature
    static const uint8_t PNG_SIG[8] = {137,80,78,71,13,10,26,10};
    if (size < 8 || memcmp(data, PNG_SIG, 8) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, PNG_LOG_TAG, "Geçersiz PNG imzası");
        return false;
    }

    int pos = 8;
    int w = 0, h = 0, colorType = 0, bitDepth = 0;
    uint8_t* idatBuf    = nullptr;
    int       idatLen   = 0;
    int       idatCap   = 0;
    bool      gotIHDR   = false;
    bool      gotIEND   = false;

    while (pos + 12 <= size && !gotIEND) {
        int chunkLen  = (int)readU32BE(data + pos); pos += 4;
        char type[5];
        memcpy(type, data + pos, 4); type[4] = 0; pos += 4;

        if (strcmp(type, "IHDR") == 0) {
            if (chunkLen < 13) { pos += chunkLen + 4; continue; }
            w          = (int)readU32BE(data + pos);
            h          = (int)readU32BE(data + pos + 4);
            bitDepth   = data[pos + 8];
            colorType  = data[pos + 9];
            gotIHDR    = true;
            __android_log_print(ANDROID_LOG_INFO, PNG_LOG_TAG,
                "IHDR: %dx%d depth=%d colorType=%d", w, h, bitDepth, colorType);

        } else if (strcmp(type, "IDAT") == 0) {
            // IDAT chunk'larını birleştir
            if (idatLen + chunkLen > idatCap) {
                idatCap = idatLen + chunkLen + 65536;
                idatBuf = (uint8_t*)realloc(idatBuf, idatCap);
            }
            memcpy(idatBuf + idatLen, data + pos, chunkLen);
            idatLen += chunkLen;

        } else if (strcmp(type, "IEND") == 0) {
            gotIEND = true;
        }

        pos += chunkLen + 4; // data + CRC
    }

    if (!gotIHDR || !gotIEND || !idatBuf) {
        free(idatBuf);
        __android_log_print(ANDROID_LOG_ERROR, PNG_LOG_TAG, "Eksik PNG chunk'ları");
        return false;
    }

    // Sadece RGBA (colorType=6) veya RGB (colorType=2) destekleniyor
    int channels = (colorType == 6) ? 4 : (colorType == 2) ? 3 : 0;
    if (channels == 0 || bitDepth != 8) {
        free(idatBuf);
        __android_log_print(ANDROID_LOG_ERROR, PNG_LOG_TAG,
            "Desteklenmeyen renk tipi=%d veya bit derinliği=%d. "
            "PNG'yi RGBA8 olarak kaydedin veya stb_image ekleyin.",
            colorType, bitDepth);
        return false;
    }

    // Filtrelenmiş ham veri (her satır 1 filtre byte'ı + piksel verisi)
    int stride    = w * channels;
    int rawSize   = h * (stride + 1);
    uint8_t* raw  = (uint8_t*)malloc(rawSize);

    if (!zlibStoredDecode(idatBuf, idatLen, raw, rawSize)) {
        free(idatBuf); free(raw);
        __android_log_print(ANDROID_LOG_ERROR, PNG_LOG_TAG,
            "zlib decode başarısız. stb_image.h ekleyerek deflate desteği kazanın.");
        return false;
    }
    free(idatBuf);

    // PNG filtre uygulama (tip 0=none, 1=sub, 2=up, 3=average, 4=paeth)
    uint8_t* pixels = (uint8_t*)malloc(w * h * 4);
    uint8_t* prev   = (uint8_t*)calloc(stride, 1);

    for (int y = 0; y < h; y++) {
        uint8_t* rowRaw = raw  + y * (stride + 1);
        uint8_t* rowOut = pixels + y * w * 4;
        uint8_t  ftype  = rowRaw[0];
        uint8_t* cur    = rowRaw + 1;

        // Filtre geri-al
        for (int x = 0; x < stride; x++) {
            uint8_t a = (x >= channels) ? cur[x - channels] : 0;
            uint8_t b = prev[x];
            uint8_t c = (x >= channels) ? prev[x - channels] : 0;
            switch (ftype) {
                case 1: cur[x] += a; break;
                case 2: cur[x] += b; break;
                case 3: cur[x] += (uint8_t)((a + b) / 2); break;
                case 4: {
                    int pa = abs((int)b - c);
                    int pb = abs((int)a - c);
                    int pc = abs((int)a + b - 2*c);
                    cur[x] += (pa <= pb && pa <= pc) ? a : (pb <= pc) ? b : c;
                    break;
                }
                default: break; // 0: None
            }
        }
        memcpy(prev, cur, stride);

        // RGB → RGBA dönüşümü
        for (int x = 0; x < w; x++) {
            rowOut[x*4+0] = cur[x*channels+0];
            rowOut[x*4+1] = cur[x*channels+1];
            rowOut[x*4+2] = cur[x*channels+2];
            rowOut[x*4+3] = (channels == 4) ? cur[x*channels+3] : 255;
        }
    }
    free(raw); free(prev);

    *outPixels = pixels;
    *outW      = w;
    *outH      = h;
    return true;
}
