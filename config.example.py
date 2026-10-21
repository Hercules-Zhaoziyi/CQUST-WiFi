"""Sanitized legacy reference only; the v3 GUI does not read this file.
Do not copy old ciphertext or session identifiers into the new authentication flow.
Never distribute a populated configuration file.
"""

loginUrl = "http://172.28.228.2/eportal/InterFace.do?method=login"
checkStatusUrl = "http://172.28.228.2/eportal/InterFace.do?method=getOnlineUserInfo"

# These keys document the original script contract, not the v3 request builder.
dataLogin = {
    "userId": "",
    "password": "",
    "service": "互联网",
    "queryString": "",
    "operatorPwd": "",
    "operatorUserId": "",
    "validcode": "",
    "passwordEncrypt": "true",
    "userIndex": "",
}
dataCheck = {"userIndex": ""}
