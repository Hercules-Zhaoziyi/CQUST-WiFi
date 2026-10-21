"""Sanitized template. Rename to config.py and fill locally if needed.
Never distribute a populated file. The GUI reads literals without executing it.
"""

loginUrl = "http://172.28.228.2/eportal/InterFace.do?method=login"
checkStatusUrl = "http://172.28.228.2/eportal/InterFace.do?method=getOnlineUserInfo"

# These keys mirror the legacy config.py contract. Values are filled in memory
# from your original config.py locally. A blank password uses the GUI input.
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
