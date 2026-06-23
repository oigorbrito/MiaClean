import math

def benchmark(name, width, height):
    # Pixels
    pixels = width * height
    mp = pixels / 1_000_000

    # --- BEFORE (Full Image) ---
    # Memory: Full bitmap in ARGB_8888
    mem_before = (pixels * 4) / (1024 * 1024)
    # Time: Decode + Compress + pHash
    # Standard values for a mid-range device:
    # Decode: ~15ms/MP, Compress: ~20ms/MP, pHash: ~40ms/MP
    t_decode_b = mp * 15
    t_compress_b = mp * 20
    t_phash_b = mp * 40
    t_total_before = t_decode_b + t_compress_b + t_phash_b

    # --- AFTER (Downscaled to 320px) ---
    target = 320
    longest_side = max(width, height)

    # inSampleSize calculation
    in_sample_size = 1
    while longest_side / (in_sample_size * 2) >= target:
        in_sample_size *= 2

    # Decoded dimensions
    dec_w = width / in_sample_size
    dec_h = height / in_sample_size
    dec_pixels = dec_w * dec_h
    dec_mp = dec_pixels / 1_000_000

    # Final scaled dimensions
    scale = target / max(dec_w, dec_h)
    final_w = int(dec_w * scale)
    final_h = int(dec_h * scale)
    final_pixels = final_w * final_h
    final_mp = final_pixels / 1_000_000

    # Memory: Peak is either the decoded bitmap or (decoded + scaled)
    # While scaling, both exist.
    mem_after = (dec_pixels * 4 + final_pixels * 4) / (1024 * 1024)

    # Time
    t_decode_a = dec_mp * 15 # Faster because of inSampleSize
    t_scale_a = dec_mp * 5   # Extra step for createScaledBitmap
    t_compress_a = final_mp * 20 # Much faster (small pixels)
    t_phash_a = final_mp * 40    # Much faster (small pixels)
    t_total_after = t_decode_a + t_scale_a + t_compress_a + t_phash_a

    return {
        "res": name,
        "size": f"{width}x{height}",
        "before_time": t_total_before,
        "before_mem": mem_before,
        "after_time": t_total_after,
        "after_mem": mem_after
    }

resolutions = [
    ("1080p", 1920, 1080),
    ("4K", 3840, 2160),
    ("12MP", 4000, 3000)
]

print(f"{'Res':<10} | {'Dim':<12} | {'Time Before':<12} | {'Time After':<12} | {'Mem Before':<12} | {'Mem After':<10}")
print("-" * 85)

results = []
for name, w, h in resolutions:
    res = benchmark(name, w, h)
    results.append(res)
    print(f"{res['res']:<10} | {res['size']:<12} | {res['before_time']:>9.1f}ms | {res['after_time']:>9.1f}ms | {res['before_mem']:>9.1f}MB | {res['after_mem']:>7.1f}MB")

print("\nGanho Percentual")
print(f"{'Res':<10} | {'Tempo (%)':<12} | {'Memória (%)':<12}")
print("-" * 40)
for res in results:
    t_gain = (res['before_time'] - res['after_time']) / res['before_time'] * 100
    m_gain = (res['before_mem'] - res['after_mem']) / res['before_mem'] * 100
    print(f"{res['res']:<10} | {t_gain:>10.1f}% | {m_gain:>10.1f}%")
