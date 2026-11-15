#!/usr/bin/env python3
"""연동·시연용 계정과 고민 목장 데이터를 공개 API로 만듭니다 (서버 코드나 DB를 직접 건드리지 않습니다).

    SEED_PASSWORD=... python scripts/seed_demo.py --base http://localhost:8080 --prefix demo --users 6

- 계정: {prefix}1@{domain} ... {prefix}N@{domain}, 닉네임 '{nickname}1' ... (이미 있으면 로그인만 합니다)
- 고민: 계정마다 서로 다른 목장에 1개씩 올립니다 (이미 진행 중인 고민이 있으면 건너뜁니다)
- 조언: 다른 계정의 고민에 돌아가며 보냅니다 (--wools 로 양 하나가 받을 조언 수를 정합니다)
- 비밀번호는 환경변수 SEED_PASSWORD 로만 받습니다. 레포나 문서에 적지 마세요.
- 다시 실행해도 됩니다. 이미 있는 계정·고민·조언은 건너뜁니다.
- --delete 를 주면 이 접두어의 계정을 탈퇴시켜 만든 데이터를 모두 지웁니다.

주의
- 서버의 LLM 이 꺼져 있으면 새 고민이 모두 '보호 양'으로 등록되어 조언 이력 5회 미만 계정에는 목록에 보이지 않습니다.
  그런 양에는 조언도 보낼 수 없어(404) 건너뛰고, 마지막에 몇 건을 건너뛰었는지 알려 줍니다.
- 운영 서버에 실행할지는 팀에서 정한 뒤에 하세요. 하루 고민 3마리, 로그인 5회 실패 잠금 같은 정책이 그대로 적용됩니다.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

WORRIES = [
    "취업 준비를 1년째 하고 있는데 계속 떨어지니까 내가 뭘 잘못하고 있는 건지 모르겠어요.",
    "친한 친구와 사소한 일로 다퉜는데 먼저 연락하기가 어려워요. 어떻게 말을 꺼내면 좋을까요?",
    "일이 끝나도 쉬는 것 같지가 않아요. 주말에도 계속 일 생각이 나서 지쳐요.",
    "다른 사람들은 다 잘 지내는 것 같은데 저만 제자리인 것 같아서 자꾸 작아져요.",
    "전공이 저와 맞지 않는 것 같은데 지금 바꾸기엔 늦은 건 아닐지 고민돼요.",
    "가족에게 속마음을 말하는 게 어려워요. 걱정시킬까 봐 괜찮은 척만 하게 돼요.",
    "해야 할 일은 많은데 손에 잡히지 않고 미루기만 해요. 그러다 보니 더 불안해져요.",
    "새로운 사람들 앞에 서면 긴장해서 말이 잘 안 나와요. 발표가 다가와서 걱정이에요.",
]
WOOLS = [
    "오늘 정말 고생 많았어요.",
    "응원할게요. 천천히 가도 괜찮아요.",
    "혼자가 아니에요. 저도 비슷한 시간을 보낸 적이 있어요.",
    "여기까지 온 것만으로도 충분히 애쓰셨어요.",
    "마음이 조금 가벼워지는 하루가 되면 좋겠어요.",
    "잠깐 쉬어 가도 괜찮아요. 쉬는 것도 나아가는 일이에요.",
    "그 마음을 꺼내 놓은 것만으로도 큰 용기예요.",
    "조금씩 나아질 거예요. 곁에서 응원하고 있을게요.",
    "오늘은 나에게 따뜻한 말 한마디 건네 보면 어때요?",
    "지금 느끼는 마음은 충분히 그럴 수 있는 마음이에요.",
]


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def call(self, method, path, token=None, body=None):
        req = urllib.request.Request(self.base + path, method=method)
        req.add_header("Accept", "application/json")
        if token:
            req.add_header("Authorization", "Bearer " + token)
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, data, timeout=40) as res:
                raw = res.read().decode("utf-8")
                return res.status, (json.loads(raw) if raw else {})
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8")
            try:
                return e.code, json.loads(raw)
            except ValueError:
                return e.code, {"raw": raw[:300]}


def error_code(body):
    return (body.get("error") or {}).get("code")


def sign_in(api, email, password, nickname):
    """로그인하고, 계정이 없으면 가입합니다. (token, created) 를 돌려줍니다."""
    status, body = api.call("POST", "/auth/login", body={"email": email, "password": password})
    if status == 200:
        return body["data"]["accessToken"], False
    if status == 423:
        sys.exit(f"{email}: 계정이 잠겨 있습니다(로그인 실패 누적). 잠시 뒤 다시 실행하세요.")
    status, body = api.call("POST", "/auth/signup",
                            body={"email": email, "password": password, "nickname": nickname, "agreed": True})
    if status in (200, 201):
        return body["data"]["accessToken"], True
    if error_code(body) == "ACCOUNT_EMAIL_DUPLICATED":
        sys.exit(f"{email}: 이미 있는 계정인데 비밀번호가 다릅니다. SEED_PASSWORD 를 확인하세요.")
    sys.exit(f"{email}: 가입 실패 {status} {body}")


def main():
    p = argparse.ArgumentParser(description="연동·시연용 계정과 고민 목장 데이터를 만듭니다.")
    p.add_argument("--base", default="http://localhost:8080", help="API 주소")
    p.add_argument("--prefix", default="demo", help="이메일 접두어 (demo → demo1@...)")
    p.add_argument("--domain", default="teolhanol.test", help="이메일 도메인")
    p.add_argument("--nickname", default="시연목동", help="닉네임 접두어 (뒤에 번호가 붙고 10자 이내여야 함)")
    p.add_argument("--users", type=int, default=6, help="계정 수 (2~8)")
    p.add_argument("--wools", type=int, default=2, help="고민 하나가 받을 조언 수 (계정 수 - 1 이하)")
    p.add_argument("--delete", action="store_true", help="이 접두어의 계정을 탈퇴시켜 데이터를 지웁니다")
    args = p.parse_args()

    password = os.environ.get("SEED_PASSWORD", "")
    if len(password) < 8:
        sys.exit("환경변수 SEED_PASSWORD 에 8자 이상 비밀번호를 넣어 주세요.")
    if not 2 <= args.users <= len(WORRIES):
        sys.exit(f"--users 는 2~{len(WORRIES)} 사이여야 합니다.")
    if len(args.nickname) + len(str(args.users)) > 10:
        sys.exit("닉네임은 번호를 붙여 10자 이내여야 합니다.")
    wools_per_sheep = max(0, min(args.wools, args.users - 1))

    api = Api(args.base)
    status, _ = api.call("GET", "/health")
    if status != 200:
        sys.exit(f"{args.base}/health 가 {status} 입니다. 주소를 확인하세요.")

    users = []
    for i in range(1, args.users + 1):
        email = f"{args.prefix}{i}@{args.domain}"
        nickname = f"{args.nickname}{i}"
        if args.delete:
            status, body = api.call("POST", "/auth/login", body={"email": email, "password": password})
            if status != 200:
                print(f"- {email}: 로그인할 수 없어 건너뜀 ({status})")
                continue
            status, _ = api.call("DELETE", "/me", body["data"]["accessToken"])
            print(f"- {email}: 탈퇴 {status}")
            continue
        token, created = sign_in(api, email, password, nickname)
        users.append({"email": email, "nickname": nickname, "token": token})
        print(f"- {email} ({nickname}): {'가입' if created else '로그인'}")
    if args.delete:
        return

    status, body = api.call("GET", "/farms", users[0]["token"])
    if status != 200:
        sys.exit(f"/farms 실패 {status} {body}")
    farms = [f["code"] for f in body["data"]]

    # 고민 올리기: 계정마다 1개 (진행 중인 고민이 이미 있으면 그 양을 씁니다)
    sheep = []
    for i, u in enumerate(users):
        status, body = api.call("GET", "/worry-sheep/me?status=active", u["token"])
        mine = [s for s in body.get("data", {}).get("sheep", []) if not s.get("hidden")] if status == 200 else []
        if mine:
            sheep.append({"id": mine[0]["sheepId"], "owner": i})
            print(f"- {u['nickname']}: 진행 중인 고민이 있어 건너뜀 (sheepId={mine[0]['sheepId']})")
            continue
        farm = farms[i % len(farms)]
        status, body = api.call("POST", f"/farms/{farm}/sheep", u["token"], {"content": WORRIES[i]})
        state = body.get("data", {}).get("status") if status in (200, 201) else error_code(body)
        if state == "CREATED":
            sheep.append({"id": body["data"]["sheepId"], "owner": i})
            print(f"- {u['nickname']}: {farm} 목장에 고민 올림 (sheepId={body['data']['sheepId']})")
        else:
            print(f"- {u['nickname']}: 고민을 올리지 못함 ({status} {state})")

    # 조언 보내기: 양마다 뒤 번호 계정들이 돌아가며 보냅니다
    sent = skipped = hidden = 0
    for k, s in enumerate(sheep):
        for step in range(1, wools_per_sheep + 1):
            sender = users[(s["owner"] + step) % len(users)]
            content = WOOLS[(k * wools_per_sheep + step) % len(WOOLS)]
            status, body = api.call("POST", f"/worry-sheep/{s['id']}/wools", sender["token"],
                                    {"content": content, "sendAsIs": True})
            state = body.get("data", {}).get("status") if status in (200, 201) else error_code(body)
            if state == "SENT":
                sent += 1
            elif state == "WOOL_ALREADY_SENT":
                skipped += 1
            elif status == 404:
                hidden += 1
            else:
                print(f"  ! sheepId={s['id']} ← {sender['nickname']}: {status} {state}")
    print(f"- 조언: 보냄 {sent}, 이미 보냄 {skipped}, 볼 수 없어 건너뜀 {hidden}")
    if hidden:
        print("  ! 조언을 보낼 수 없는 양이 있습니다. 서버의 LLM 이 꺼져 있으면 새 고민이 '보호 양'으로 등록되어")
        print("    조언 이력 5회 미만 계정에는 보이지 않습니다. LLM 키를 확인한 뒤 --delete 후 다시 실행하세요.")

    status, body = api.call("GET", "/farms/all/sheep?page=0", users[0]["token"])
    visible = len(body.get("data", {}).get("sheep", [])) if status == 200 else "?"
    print(f"- 확인: {users[0]['nickname']} 에게 보이는 풀밭 양 {visible}마리")
    print("계정 이메일은 위 목록과 같고, 비밀번호는 실행할 때 넣은 SEED_PASSWORD 입니다.")


if __name__ == "__main__":
    main()
