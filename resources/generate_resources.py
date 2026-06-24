#!/usr/bin/env python3
"""
驾考模拟器 - 资源占位文件生成脚本
运行: python3 generate_resources.py
输出: placeholder_assets/ 目录
复制到 app/src/main/assets/ 即可通过 AAssetManager 加载
"""

import struct, zlib, os, math

OUT_DIR = os.path.join(os.path.dirname(__file__), "placeholder_assets")
os.makedirs(OUT_DIR, exist_ok=True)

def chunk(ctype, data):
    c = ctype + data
    return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)

def png(path, r, g, b, w=64, h=64):
    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    raw = b""
    for _ in range(h):
        raw += b"\x00" + bytes([r, g, b]) * w
    idat = chunk(b"IDAT", zlib.compress(raw))
    iend = chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(sig + ihdr + idat + iend)

def obj(path, verts, norms, faces):
    with open(path, "w") as f:
        for v in verts:
            f.write(f"v {v[0]:.4f} {v[1]:.4f} {v[2]:.4f}\n")
        for n in norms:
            f.write(f"vn {n[0]:.4f} {n[1]:.4f} {n[2]:.4f}\n")
        for face in faces:
            f.write("f " + " ".join(f"{fi}//{fi}" for fi in face) + "\n")

def wav(path, dur=0.5, sr=44100):
    ns = int(sr * dur)
    ds = ns * 2
    with open(path, "wb") as f:
        f.write(b"RIFF" + struct.pack("<I", 36 + ds) + b"WAVE")
        f.write(b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, sr, sr * 2, 2, 16))
        f.write(b"data" + struct.pack("<I", ds) + b"\x00" * ds)

# --- 纹理 ---
TEX = {
    "car_body.png": (220,220,220), "car_glass.png": (100,150,200),
    "car_wheel.png": (40,40,40), "ground_asphalt.png": (60,60,60),
    "ground_concrete.png": (180,180,170), "ground_grass.png": (76,175,80),
    "ground_line.png": (255,255,255), "ground_curb.png": (200,50,50),
    "building_wall.png": (200,190,170), "building_roof.png": (150,50,30),
    "skybox_up.png": (30,60,120), "skybox_side.png": (80,140,230),
    "skybox_down.png": (80,80,80),
}
for n, c in TEX.items():
    png(os.path.join(OUT_DIR, n), c[0], c[1], c[2])
    print(f"  [TEX] {n}")

# --- 模型 ---
N_UP = (0,1,0)

# 教练车
cv = [(-2,0,0.9),(2,0,0.9),(2,1.5,0.9),(-2,1.5,0.9),
      (-2,0,-0.9),(2,0,-0.9),(2,1.5,-0.9),(-2,1.5,-0.9),
      (-1,1.5,0.5),(1,1.5,0.5),(1,2.5,0.5),(-1,2.5,0.5),
      (-1,1.5,-0.5),(1,1.5,-0.5),(1,2.5,-0.5),(-1,2.5,-0.5)]
cn = [N_UP]*16
cf = [(1,2,3,4),(5,8,7,6),(1,5,6,2),(2,6,7,3),(3,7,8,4),(4,8,5,1),
      (9,10,11,12),(13,16,15,14),(9,13,14,10),(10,14,15,11),(11,15,16,12),(12,16,13,9)]
obj(os.path.join(OUT_DIR, "car_body.obj"), cv, cn, cf)

# 倒车入库
rv = [(-5,0,-8),(5,0,-8),(5,0,8),(-5,0,8)]
obj(os.path.join(OUT_DIR, "subject2_reversing.obj"), rv, [N_UP]*4, [(1,2,3,4)])

# 侧方停车
obj(os.path.join(OUT_DIR, "subject2_sidepark.obj"),
    [(-3,0,-6),(3,0,-6),(3,0,6),(-3,0,6)], [N_UP]*4, [(1,2,3,4)])

# 坡道
rv2 = []
for z in range(-5, 6):
    h = max(0, (z+5)*0.15)
    rv2.append((-3.5,h,z)); rv2.append((3.5,h,z))
obj(os.path.join(OUT_DIR, "subject2_ramp.obj"), rv2, [N_UP]*len(rv2),
    [(i*2+1,i*2+2,(i+1)*2+2,(i+1)*2+1) for i in range(10)])

# S弯
sv = []
for i in range(40):
    t = i/39*math.pi*2; x = math.sin(t)*12; z = t*6-12
    sv.append((x-2.5,0,z)); sv.append((x+2.5,0,z))
obj(os.path.join(OUT_DIR, "subject2_curve.obj"), sv, [N_UP]*len(sv),
    [(i*2+1,i*2+2,(i+1)*2+2,(i+1)*2+1) for i in range(39)])

# 直角转弯
obj(os.path.join(OUT_DIR, "subject2_turn.obj"),
    [(-3.5,0,0),(3.5,0,0),(3.5,0,10),(-3.5,0,10),
     (3.5,0,0),(10,0,0),(10,0,3.5),(3.5,0,3.5)], [N_UP]*8,
    [(1,2,3,4),(5,6,7,8)])

# 科三道路
obj(os.path.join(OUT_DIR, "subject3_road.obj"),
    [(-5,0,-750),(5,0,-750),(5,0,750),(-5,0,750)], [N_UP]*4, [(1,2,3,4)])

# 交通锥
cv2 = []
for i in range(8):
    a = i/8*math.pi*2
    cv2.append((0.3*math.cos(a), 0, 0.3*math.sin(a)))
    cv2.append((0.05*math.cos(a), 1, 0.05*math.sin(a)))
obj(os.path.join(OUT_DIR, "prop_cone.obj"), cv2, [N_UP]*len(cv2),
    [(i*2+1,i*2+2,((i+1)%8)*2+2,((i+1)%8)*2+1) for i in range(8)])

print("  [OBJ] 7 models")

# --- 音频 ---
for n, d in [("engine_idle.wav", 2.0), ("engine_accel.wav", 1.5),
              ("horn.wav", 0.3), ("turn_signal.wav", 0.4),
              ("exam_pass.wav", 1.0), ("exam_fail.wav", 1.0),
              ("bg_city.wav", 5.0)]:
    wav(os.path.join(OUT_DIR, n), d)
    print(f"  [WAV] {n}")

print(f"\nDone! Generated {len(TEX)+7+7} files into {OUT_DIR}")
print("Copy to: app/src/main/assets/")
