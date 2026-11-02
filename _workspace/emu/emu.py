# 에뮬레이터 조작 도우미: python3 -I emu.py <cmd> [args]
import subprocess,sys,re,time,os
A=os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
def sh(*a,check=False): return subprocess.run([A,*a],capture_output=True,text=True).stdout
def dump():
    sh("shell","uiautomator","dump","/sdcard/ui.xml"); return sh("shell","cat","/sdcard/ui.xml")
def nodes(x):
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x):
        t,d,a,b,c,e=m.groups(); yield t,d,(int(a)+int(c))//2,(int(b)+int(e))//2
def find(q,x=None):
    x=x or dump()
    for t,d,cx,cy in nodes(x):
        if q in t or q in d: return cx,cy
cmd=sys.argv[1]
if cmd=="texts":
    for t,d,cx,cy in nodes(dump()):
        if t or d: print(f"{cx},{cy} | {t} | {d}")
elif cmd=="tap":
    p=None
    for i in range(int(sys.argv[3]) if len(sys.argv)>3 else 10):
        p=find(sys.argv[2])
        if p: break
        time.sleep(1)
    if not p: print("NOTFOUND",sys.argv[2]); sys.exit(1)
    sh("shell","input","tap",str(p[0]),str(p[1])); print("TAP",sys.argv[2],p)
elif cmd=="wait":
    for i in range(int(sys.argv[3]) if len(sys.argv)>3 else 20):
        if find(sys.argv[2]): print("FOUND",sys.argv[2]); sys.exit(0)
        time.sleep(1)
    print("TIMEOUT",sys.argv[2]); sys.exit(1)
elif cmd=="shot":
    sh("shell","screencap","-p","/sdcard/s.png"); sh("pull","/sdcard/s.png",sys.argv[2]); print("SHOT",sys.argv[2])
