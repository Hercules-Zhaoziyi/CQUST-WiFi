"""Reference only: the GUI does not store credentials in this module."""

loginUrl = "http://172.28.228.2/eportal/InterFace.do?method=login"
checkStatusUrl = "http://172.28.228.2/eportal/InterFace.do?method=getOnlineUserInfo"

# These keys mirror the legacy config.py contract. Values are filled in memory
# by auth_adapter.py after the GUI receives input and discovers queryString.
dataLogin = {
    "userId": "",
    "password": "",
    "service": "",
    "queryString": "",
    "operatorPwd": "",
    "operatorUserId": "",
    "validcode": "",
    "passwordEncrypt": "false",
    "userIndex": "",
}
dataCheck = {"userIndex": ""}
