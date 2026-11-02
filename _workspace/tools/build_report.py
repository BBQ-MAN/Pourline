# 사용자 검토 보고서(단일 HTML) 생성: python3 -I _workspace/tools/build_report.py
import json, base64, subprocess, os, html, re, tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "docs/review/TimemoreDotScaleApp-review-20261102.html")
TMP = tempfile.mkdtemp(prefix="report_img_", dir=os.path.join(ROOT, "_workspace"))
def E(x): return html.escape(str(x).replace("`", ""))


def img(path, maxdim=720, q=72):
    src = os.path.join(ROOT, path)
    dst = os.path.join(TMP, os.path.basename(path) + ".jpg")
    subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", str(q), "-Z", str(maxdim), src, "--out", dst],
                   capture_output=True, check=True)
    return "data:image/jpeg;base64," + base64.b64encode(open(dst, "rb").read()).decode()


def fig(path, cap, cls=""):
    return f'<figure class="{cls}"><img loading="lazy" src="{img(path)}" alt="{E(cap)}"><figcaption>{E(cap)}</figcaption></figure>'


meta = json.load(open(os.path.join(ROOT, "_workspace/run_meta.json")))
ms = json.load(open(os.path.join(ROOT, "docs/spec/milestones.json")))
ms = ms["milestones"] if isinstance(ms, dict) else ms

# ---- 결정 대기 목록 ----
pending = [d for d in meta["decisions"] if str(d.get("by", "")).startswith("하네스 추천")]
def opts(d):
    o = d.get("options", "")
    return " / ".join(o) if isinstance(o, list) else str(o)
dec_rows = "".join(
    f"<tr><td class='id'>{E(d['id'])}</td><td>{E(d['decision'])}</td><td>{E(opts(d))}</td>"
    f"<td>{E(d.get('basis',''))}</td><td>{E(d.get('revertCost',''))}</td></tr>" for d in pending)

# ---- 마일스톤 ----
label = {"done": "완료", "awaiting-device": "실기기 대기", "todo": "할 일", "blocked": "막힘", "needs-decision": "판단 필요"}
ms_rows = "".join(
    f"<tr><td class='id'>{E(m['id'])}</td><td>{E(m['title'])}</td><td><span class='st {m['status']}'>{label.get(m['status'], m['status'])}</span></td>"
    f"<td>{sum(1 for a in m['acceptance'] if a.startswith('[실기기'))}</td></tr>" for m in ms)

# ---- 실기기 체크리스트(마일스톤의 [실기기] 조건) ----
dev_items = []
for m in ms:
    for a in m["acceptance"]:
        if a.startswith("[실기기"):
            tag = re.match(r"\[실기기[^\]]*\]", a).group(0)
            dev_items.append((m["id"], tag, a[len(tag):].strip()))
dev_rows = "".join(f"<li><input type='checkbox' disabled> <b>{E(t)}</b> <span class='muted'>({E(i)})</span> {E(x)}</li>" for i, t, x in dev_items)

# ---- 스크린샷 ----
rob = [
    ("brew_idle_light.png", "S1 대기(라이트): 빈 서버 + 목표 표식"),
    ("brew_idle_dark.png", "S1 대기(다크)"),
    ("brew_idle_light_font200.png", "S1 대기, 글꼴 200%"),
    ("brew_idle_landscape_dark.png", "S1 가로 대기(다크)"),
    ("brew_running_recipe_light.png", "S1 추출 중: 리본 그래프(굵기 = 유량) + 단계 카드"),
    ("brew_running_recipe_landscape_light.png", "S1 가로 추출 중: 지표 24 sp 상한"),
    ("brew_running_recipe_landscape_light_font200.png", "S1 가로, 글꼴 200%"),
    ("brew_running_valve_dark.png", "밸브 칸 '2:00에 열기' + 기다리기 목표 점선(P6)"),
    ("brew_running_valve_cue_light.png", "밸브 바뀌는 순간(색 숨)"),
    ("brew_running_valve_landscape_light.png", "가로 밸브 칸 한 줄(P1·P4)"),
    ("brew_running_valve_light_font200.png", "글꼴 200% 밸브 띠"),
    ("brew_reconnecting_running_recipe_dark.png", "재연결 배너 + 멈춘 값 + 값 없는 구간"),
    ("brew_overload_dark.png", "과부하 배너"),
    ("brew_permission_permanently_denied_dark.png", "권한 영구 거부 배너"),
    ("connect_scanning_results_light.png", "S2 저울 찾기 후보"),
    ("connect_failed_dark.png", "S2 연결 실패"),
    ("device_sheet_connected_dark.png", "S3 저울 정보 시트"),
    ("summary_ready_light.png", "S6 추출 요약"),
    ("summary_save_failed_light_font200.png", "S6 저장 실패, 글꼴 200%"),
    ("recipes_first_run_dark.png", "S7 기본 레시피 5개(다크)"),
    ("recipes_brewing_light.png", "S7 추출 중 배너(P10)"),
    ("recipe_edit_valve_light.png", "S8 스위치 레시피 편집"),
    ("recipe_edit_invalid_light_font200.png", "S8 오류, 글꼴 200%(P13 세로 쌓기)"),
    ("history_loaded_dark.png", "S9 기록 목록(다크)"),
    ("session_detail_completed_light.png", "S10 기록 상세"),
    ("brew_leave_confirm_light_font200.png", "D1 추출 중단 확인, 글꼴 200%"),
]
emu = [
    ("01_s1_nodevice_light.png", "앱 시작(데모 진입), 저울 없음"),
    ("02_s2_scan_demo_light.png", "S2 데모 저울 6개 발견"),
    ("03_s1_connected_free_light.png", "데모 하리오 연결, 영점 전 213.4 g"),
    ("05_s1_idle_valve_light.png", "하리오 선택: 밸브 칸 '닫힘'"),
    ("06_s1_tared_light.png", "영점 → 0.0 g, 자동 시작 대기"),
    ("07_s1_running_10s_light.png", "붓기 → 자동 시작"),
    ("09_s1_valve_countdown_light.png", "밸브 '열기까지 1초'"),
    ("11_after_brew_light.png", "2:30 자동 완료 → S6"),
    ("13_s9_history_light.png", "저장 → S9 기록"),
    ("14_s10_detail_light.png", "S10 상세: 단계·스위치 일정"),
    ("15_csv_picker.png", "CSV 내보내기: 시스템 파일 선택기"),
    ("17_s10_detail_dark.png", "S10 상세(다크)"),
    ("19_s7_recipes_dark.png", "S7 레시피(다크)"),
    ("20_s8_edit_devil_dark.png", "S8 악마의 레시피 편집(다크)"),
    ("21_s8_edit_devil_steps_dark.png", "S8 단계 카드(다크)"),
    ("23_s1_running_valve_dark.png", "추출 중 밸브(다크)"),
    ("24_s1_running_valve_landscape_dark.png", "가로 추출 중(다크): 한 줄 밸브 칸"),
    ("25_s6_summary_dark.png", "S6 요약(다크)"),
    ("26_s1_idle_font200_light.png", "글꼴 200% S1"),
    ("27_s7_recipes_font200_light.png", "글꼴 200% S7"),
    ("28_s1_permission_missing_light.png", "근처 기기 권한 거부(pm revoke)"),
]
rob_html = "".join(fig("screenshots/" + f, c, "land" if "landscape" in f else "") for f, c in rob)
emu_html = "".join(fig("_workspace/emu/shots/" + f, c, "land" if "landscape" in f else "") for f, c in emu)
icon_src = img("_workspace/icon/app_icon_v1.png", 256, 80)

n_shots = len([f for f in os.listdir(os.path.join(ROOT, "screenshots")) if f.endswith(".png")])
done = sum(1 for m in ms if m["status"] == "done"); waiting = sum(1 for m in ms if m["status"] == "awaiting-device")

CSS = """
:root{--bg:#FFF8F5;--fg:#211A16;--muted:#52443B;--card:#FCF1EB;--line:#D7C3B7;--accent:#6F4A2E;--ok:#2F6A5A;--okbg:#B4EFDC;--warn:#7A4E00;--warnbg:#FFDDB3;--chip:#F8DECF}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#18120E;--fg:#EEE0D9;--muted:#D7C3B7;--card:#251E1A;--line:#52443B;--accent:#F0BC94;--ok:#98D3C0;--okbg:#135143;--warn:#FFDDB3;--warnbg:#5C3B00;--chip:#3B332E}}
:root[data-theme="dark"]{--bg:#18120E;--fg:#EEE0D9;--muted:#D7C3B7;--card:#251E1A;--line:#52443B;--accent:#F0BC94;--ok:#98D3C0;--okbg:#135143;--warn:#FFDDB3;--warnbg:#5C3B00;--chip:#3B332E}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.6 -apple-system,"Apple SD Gothic Neo","Noto Sans KR",Roboto,sans-serif;word-break:keep-all}
main{max-width:1100px;margin:0 auto;padding:24px 16px 80px}h1{font-size:28px;line-height:1.25;margin:8px 0}h2{font-size:21px;margin:48px 0 12px;padding-top:12px;border-top:1px solid var(--line)}h3{font-size:17px;margin:24px 0 8px}
.lede{font-size:17px}.muted{color:var(--muted)}.card{background:var(--card);border-radius:20px;padding:16px 20px;margin:12px 0}
.hero{display:flex;gap:20px;align-items:center;flex-wrap:wrap}.hero img{width:96px;height:96px;border-radius:24px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:14px}.grid figure{margin:0}.grid figure.land{grid-column:span 2}
figure img{width:100%;border-radius:12px;border:1px solid var(--line);display:block}figcaption{font-size:12.5px;color:var(--muted);margin-top:4px}
.tw{overflow-x:auto;-webkit-overflow-scrolling:touch}table{border-collapse:collapse;width:100%;font-size:13.5px}th,td{border-bottom:1px solid var(--line);padding:8px 6px;text-align:left;vertical-align:top}th{font-weight:600;white-space:nowrap}
td.id{font-weight:600;white-space:nowrap}.st{padding:2px 8px;border-radius:10px;font-size:12px;white-space:nowrap}.st.done{background:var(--okbg);color:var(--ok)}.st.awaiting-device{background:var(--warnbg);color:var(--warn)}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}.kpi{background:var(--card);border-radius:16px;padding:12px 14px}.kpi b{display:block;font-size:24px}
ul.check{list-style:none;padding-left:0}ul.check li{margin:6px 0}code{background:var(--chip);padding:1px 5px;border-radius:6px;font-size:12.5px;word-break:break-all}
.two{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media (max-width:700px){.two{grid-template-columns:1fr}.grid figure.land{grid-column:span 2}}
nav a{color:var(--accent);margin-right:12px;white-space:nowrap}
"""

H = f"""<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Timemore 앱 검토</title><style>{CSS}</style></head><body><main>
<div class="hero"><img src="{icon_src}" alt="앱 아이콘"><div><div class="muted">TIMEMORE Dot 저울 Android 앱 · 사용자 검토 보고서 · 2026-11-02</div>
<h1>앱 구현과 자동 검증은 끝났고, 실제 저울 검증과 결정 {len(pending)}건 검토가 남았습니다</h1></div></div>
<p class="lede">화면 11종(S1~S10, D1~D6)과 디버그 데모 저울까지 구현했습니다. 단위 테스트 670개와 Roborazzi 스크린샷 {n_shots}장, 에뮬레이터 계측 테스트 7개가 모두 통과합니다. 사용자 결정 없이 진행하라는 지시에 따라 고른 추천안 {len(pending)}건은 아래 2절에서 확정하거나 바꿔 주세요.</p>
<nav><a href="#s1">1 상태</a><a href="#s2">2 결정 대기</a><a href="#s3">3 화면</a><a href="#s4">4 검증</a><a href="#s5">5 실기기 절차</a><a href="#s6">6 다음 단계</a></nav>

<h2 id="s1">1. 현재 상태</h2>
<div class="kpis"><div class="kpi"><b>{done}/{len(ms)}</b>마일스톤 완료</div><div class="kpi"><b>{waiting}</b>실기기 확인만 남음</div><div class="kpi"><b>670 / 0</b>단위 테스트 / 실패</div><div class="kpi"><b>7 / 7</b>에뮬레이터 계측 테스트</div><div class="kpi"><b>{n_shots}</b>Roborazzi 스크린샷</div></div>
<div class="card"><b>APK</b> (2026-11-02 06:58 빌드)<br>
디버그(데모 저울 포함): <code>app/build/outputs/apk/debug/app-debug.apk</code> (13.7 MB)<br>
릴리스(서명 안 됨, 데모 코드 0건): <code>app/build/outputs/apk/release/app-release-unsigned.apk</code> (9.6 MB)<br>
<span class="muted">디버그 APK를 설치하면 런처 아이콘이 둘입니다: 'Timemore Dot'(실제 BLE)과 'Timemore 데모'(데모 저울).</span></div>
<div class="two"><div class="card"><h3>동작하는 기능</h3><ul>
<li>저울 찾기·연결·자동 재연결, 저울 정보(배터리·펌웨어·단위)</li>
<li>실시간 무게·타이머·유량, 영점, 붓기 감지 자동 시작, 레시피 끝 시각 뒤 자동 완료</li>
<li>리본 그래프(굵기 = 유량)와 목표선, 단계 카드(기다리기 단계 포함)</li>
<li>스위치(밸브) 레시피: 단계별 열림/닫힘, 카운트다운, 진동·TalkBack 알림</li>
<li>추출 요약·저장, 미저장 추출 복구, 기록 목록·상세, CSV 내보내기(요약·샘플)</li>
<li>레시피 목록·편집(기본 레시피 5개), 추출 설정</li>
<li>다크 모드, 가로, 글꼴 200%, TalkBack 낭독·포커스 순서</li>
<li>디버그 데모 저울 6가지 시나리오(V60, 하리오, 끊김·재연결, 과부하, 블루투스 꺼짐, 연결 실패)</li>
<li>앱 아이콘(Codex CLI 이미지 생성, 글자·상표 없음, 출처 <code>_workspace/icon/SOURCE.md</code>)</li></ul></div>
<div class="card"><h3>구현하지 않은 것(MVP 밖)</h3><ul>
<li>앱에서 g/oz 단위 바꾸기, 저울 이름 바꾸기·전원 끄기·공장 초기화·페어링 잊기</li>
<li>본딩, 포그라운드 서비스(화면을 끄고 추출), 클라우드 동기화, 레시피 공유</li>
<li>서명된 릴리스 빌드와 스토어 배포 설정</li>
<li>테마 아이콘용 단색(monochrome) 레이어</li></ul>
<p class="muted">architecture.md 11절 '하지 않는 것' 기준.</p></div></div>
<div class="tw"><table><thead><tr><th>마일스톤</th><th>내용</th><th>상태</th><th>실기기 조건</th></tr></thead><tbody>{ms_rows}</tbody></table></div>

<h2 id="s2">2. 사용자 최종 결정 대기 목록 ({len(pending)}건)</h2>
<p>모두 '하네스 추천(잠정)'으로 정해 구현까지 마쳤습니다. 바꾸고 싶은 항목은 ID로 알려 주세요. 근거와 되돌리는 비용은 <code>_workspace/run_meta.json</code>의 decisions와 같습니다. 이미 사용자가 확정한 결정(유기적 디자인 B+A, D6 개정, 다크 프로토타입, 자동 완료 시 S6, 단계 체크 밸브, 밸브 알림 Assertive, 가로 지표 24 sp 등)은 바꾸지 않았습니다.</p>
<div class="card"><b>먼저 볼 항목</b><ul>
<li><b>DEMO-D1</b>: 지시 원문은 'FakeScaleClient 기반'이었지만, 가짜 연결 계층을 실제 BLE 클라이언트에 끼우는 방식으로 바꿨습니다. 명령 응답·재연결·블루투스 처리 코드를 실기기와 같게 지나게 하려는 것입니다.</li>
<li><b>P2·P5</b>: 기본 레시피 단계를 바꿨거나(P2), 바꾸지 않고 두었습니다(P5). 레시피 해석이 맞는지 확인이 필요합니다.</li>
<li><b>P12</b>: 최종 QA에서 찾은 앱 종료 위험(Android 12 이상에서 권한 없이 '블루투스 켜기')을 고친 방식입니다.</li></ul></div>
<div class="tw"><table><thead><tr><th>ID</th><th>채택안</th><th>선택지</th><th>근거</th><th>바꾸면 드는 비용</th></tr></thead><tbody>{dec_rows}</tbody></table></div>

<h2 id="s3">3. 화면</h2>
<h3>Roborazzi 스크린샷(대표 {len(rob)}장, 전체 {n_shots}장은 <code>screenshots/</code>)</h3>
<div class="grid">{rob_html}</div>
<h3>에뮬레이터 실행 화면(데모 저울, AVD Medium_Phone_API_37.0)</h3>
<p class="muted">실제 앱을 설치해 데모 저울로 연결 → 영점 → 자동 시작 → 밸브 안내 → 자동 완료 → 저장 → 기록 → CSV 순서로 조작했습니다. 밸브가 열리는 순간은 화면 정보를 읽는 데 2~3초가 걸려 캡처를 놓쳤습니다. 그 장면은 위 Roborazzi 'brew_running_valve_cue'와 계측 테스트 DemoValveTest(2:00 전 '닫힘', 뒤 '열림')로 확인했습니다.</p>
<div class="grid">{emu_html}</div>

<h2 id="s4">4. 검증 결과</h2>
<div class="tw"><table><thead><tr><th>명령</th><th>결과</th><th>로그</th></tr></thead><tbody>
<tr><td><code>verify.sh test lintDebug assembleDebug --rerun-tasks</code></td><td>통과(캐시 없이 전체 실행)</td><td><code>_workspace/build/verify_20261102_065751.log</code></td></tr>
<tr><td><code>verify.sh :app:verifyRoborazziDebug</code></td><td>통과({n_shots}장 비교)</td><td><code>_workspace/build/verify_20261102_065837.log</code></td></tr>
<tr><td><code>verify.sh :app:assembleRelease</code> + release dex 데모 패턴 grep</td><td>통과, 데모 클래스 0건(디버그는 32건)</td><td><code>_workspace/build/verify_20261102_065838.log</code></td></tr>
<tr><td><code>:app:connectedDebugAndroidTest</code>(에뮬레이터, DEMO-3)</td><td>7개 통과, 연속 2회, 건너뜀 0</td><td><code>_workspace/03_report_wf_49f8767a-590.md</code></td></tr>
</tbody></table></div>
<p>단위 테스트: :app 382, :core:ble 84, :core:brew 75, :core:protocol 129. 합계 670개, 실패·오류·건너뜀 0.</p>
<h3>리뷰 지적과 처리</h3><ul>
<li>마일스톤마다 구현 → QA → ECC 코드·접근성·시각 리뷰 → 지적 교차 검증 → 수정 루프를 돌렸습니다. 이번 세션에서 워크플로 6회, 에이전트 약 390개를 썼습니다.</li>
<li>리뷰가 라운드마다 새 지적을 내 수렴하지 않은 M8·M10·M11은 P9 절차로 마무리했습니다. 구현자가 고친 뒤 QA 1명이 지적별로 다시 확인했습니다.</li>
<li><b>최종 통합 QA</b>(<code>_workspace/05_final_qa.md</code>): high 1건(Q10, Android 12 이상에서 권한 없이 블루투스 켜기를 누르면 앱 종료 가능)을 P12로 고쳤습니다. medium 0건, low 5건입니다. 화면 사이 흐름과 P1~P11 반영은 모두 일치했습니다.</li>
<li><b>최종 시각 디자인 검증</b>(<code>_workspace/05_final_design_review.md</code>): D1~D11과 WCAG AA 위반은 0건입니다. medium 4건 중 2건을 P13으로 고쳤고, 2건은 알려진 문제로 남겼습니다. low는 13건입니다.</li></ul>
<h3>알려진 문제</h3><ul>
<li>대화상자 폭: 문구가 길면 D1·D2·D5가 360 dp 화면 폭 끝까지 퍼집니다. Robolectric 렌더링 특성일 수 있어 실기기에서 확인해야 합니다(P13).</li>
<li>너비 220 dp·글꼴 200%라는 극단 조건에서 한글이 음절 중간에서 줄바꿈됩니다(P13).</li>
<li>6888 2·3차 붓기 뒤와 테츠 카스야 기본 레시피는 레시피대로 따라도 '늦음'이 최대 약 20 s·40 s 뜰 수 있습니다(P5).</li>
<li>다크 모드에서 파괴적 동작(빨강)과 일반 동작 색이 비슷해 보이고, 스낵바·FAB 그림자가 명세의 '그림자 없음'과 다릅니다(디자인 low).</li>
<li>S4 설정은 데모 모드와 실제 모드가 같은 저장소를 씁니다(QA low).</li></ul>
<div class="two"><div class="card"><h3>에뮬레이터로 확인한 것</h3><ul>
<li>데모 저울로 실제 디코더·재조립기·상태 환원기·BLE 클라이언트·추출 엔진을 거치는 전체 흐름(바이트 로그 RX FFF1 / TX FFF2)</li>
<li>연결, 영점(영점 전 무게 3프레임 뒤 0 g), 자동 시작, 단계 진행, 밸브 안내, 자동 완료, 저장, 기록, CSV 파일(BOM과 헤더)</li>
<li>오류 화면: 과부하, 끊김 → NoData → 재연결, 블루투스 꺼짐(데모 사건), 연결 실패, 근처 기기 권한 거부(pm revoke)</li>
<li>라이트·다크·가로·글꼴 200% 표시</li></ul></div>
<div class="card"><h3>실기기·실제 저울로만 확인할 수 있는 것</h3><ul>
<li>실제 BLE 스캔·연결·재연결과 Android에서의 끊김 감지 시간(B1·B3·B6)</li>
<li>100 ms 알림 간격 유지와 10분 연속 측정 누락률(B10)</li>
<li>펌웨어 판에 따른 동작(영점 전 무게 프레임 수 C-0D-2, 타이머 응답)과 전원 재투입 뒤 방송</li>
<li>시스템 블루투스 켜기 대화상자, 실제 권한 흐름, 진동(터치 진동 설정), TalkBack 실제 낭독(B12)</li>
<li>배터리 소모·백그라운드·화면 회전 중 연결 유지(B9), 실제 추출 3분(B8)과 CSV를 다른 앱에서 열기(B11)</li></ul></div></div>

<h2 id="s5">5. 사용자 실기기 검증 절차</h2>
<div class="card"><b>준비</b><ol>
<li>Android 12 이상 휴대폰에서 개발자 옵션과 USB 디버깅을 켜고 Mac에 연결합니다. <code>adb devices</code>에 기기가 보여야 합니다.</li>
<li>설치: <code>./gradlew installDebug</code>(또는 <code>adb install -r app/build/outputs/apk/debug/app-debug.apk</code>). 실제 저울 확인은 'Timemore Dot' 아이콘으로 합니다('Timemore 데모'가 아님).</li>
<li>로그 수집: 시나리오마다 <code>adb logcat -c</code>로 비운 뒤, 끝나면 <code>adb logcat -d -s TimemoreBle:V &gt; _workspace/04_device_b_&lt;시나리오&gt;.log</code>로 저장합니다.</li>
<li>결과를 이 세션에 알려 주시면 <code>_workspace/04_device_results.md</code>에 정리하고, 통과한 마일스톤을 완료로 바꿉니다.</li></ol></div>
<h3>시나리오(device-verification B1~B12)</h3>
<ul class="check">
<li><input type="checkbox" disabled> <b>B1</b> 첫 실행에서 권한을 허용하고 스캔·연결 → 10초 안에 연결, 무게가 저울 표시와 같음</li>
<li><input type="checkbox" disabled> <b>B2</b> 권한 거부, '다시 묻지 않음' → 해당 상태와 설정 이동 버튼</li>
<li><input type="checkbox" disabled> <b>B2-b</b>(P12) 권한 없음 + 블루투스 꺼짐에서 '블루투스 켜기' → 앱이 종료되지 않고 권한 요청이 먼저 뜸</li>
<li><input type="checkbox" disabled> <b>B3</b> 블루투스를 끈 채 시작하고, 실행 중 끄기·켜기 → 꺼짐 표시, 켜면 자동 재연결</li>
<li><input type="checkbox" disabled> <b>B4</b> 앱에서 영점 → 저울 0, 응답 로그. 무게를 올린 채 5회 이상 반복해 영점 전 무게 프레임 수 기록</li>
<li><input type="checkbox" disabled> <b>B5</b> 타이머 시작·일시정지·초기화 → 앱과 저울 타이머 일치</li>
<li><input type="checkbox" disabled> <b>B6</b> 추출 중 저울 전원 끄기·켜기 → 재연결 중 표시, 자동 재연결, 추출 유지</li>
<li><input type="checkbox" disabled> <b>B7</b> 저울에서 단위를 oz로 바꿈 → 앱 표시가 명세대로 바뀜</li>
<li><input type="checkbox" disabled> <b>B8</b> 레시피를 따라 3분 추출 → 단계 진행, 그래프 끊김 없음, 기록 저장</li>
<li><input type="checkbox" disabled> <b>B9</b> 추출 중 화면 회전, 홈 갔다 오기 → 연결·타이머 유지</li>
<li><input type="checkbox" disabled> <b>B10</b> 10분 연속 측정 → 알림 누락 1% 미만, 메모리 증가 없음</li>
<li><input type="checkbox" disabled> <b>B11</b> 기록 CSV 내보내기 → 다른 앱에서 열리고 헤더·단위가 명세와 같음</li>
<li><input type="checkbox" disabled> <b>B12</b> TalkBack을 켜고 추출 → 숫자를 매번 읽지 않고, 끊김·영점·타이머·밸브 변화를 알림</li>
<li><input type="checkbox" disabled> <b>밸브</b> 하리오 스위치 기본으로 추출 → 2:00에 '열림'으로 바뀌고 터치 진동 1회. S4에서 진동을 끄면 진동 없음</li>
<li><input type="checkbox" disabled> <b>모션 줄이기</b> 개발자 옵션 애니메이터 배율 0 → 리본 머리가 계단식으로 최신 점에 붙고 전환 애니메이션이 없음</li>
<li><input type="checkbox" disabled> <b>디자인</b> D1·D2·D5 대화상자 폭이 화면 끝까지 퍼지는지(P13), 한글 줄바꿈</li></ul>
<details><summary>마일스톤별 실기기 완료 조건 원문({len(dev_items)}개)</summary><ul class="check">{dev_rows}</ul></details>

<h2 id="s6">6. 다음 단계 제안</h2><ol>
<li>2절의 결정 {len(pending)}건을 확인합니다. 바꾸고 싶은 ID만 알려 주시면 해당 명세와 코드를 고칩니다. 대부분 되돌리는 비용이 낮습니다.</li>
<li>실기기와 실제 저울로 5절 시나리오를 확인합니다. 실기기 대기 중인 M4·M7·M8·M9·M11은 이 결과로 완료됩니다.</li>
<li>실기기 결과에 따라 FIX 마일스톤을 추가합니다. 특히 B6 끊김 감지 시간과 C-0D-2 프레임 수는 엔진 설정값에 영향을 줍니다.</li>
<li>배포 준비: 서명 키와 릴리스 설정, 앱 이름 상표 검토('Timemore Dot' 라벨), 테마 아이콘 단색 레이어, Play 정책·데이터 안전 항목.</li>
<li>선택: 알려진 문제(대화상자 폭, P5 레시피 기다림, 다크 파괴적 동작 색, 그림자) 정리.</li></ol>
<p class="muted">근거 문서: docs/spec/protocol.md·architecture.md·ui-design.md, docs/spec/milestones.json, _workspace/03_report_*.md, _workspace/05_final_*.md, _workspace/run_meta.json. 커밋·푸시는 하지 않았습니다(git 저장소가 아님).</p>
</main></body></html>"""

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w").write(H)
print(OUT, os.path.getsize(OUT), "bytes", len(pending), "decisions", len(dev_items), "device items")
