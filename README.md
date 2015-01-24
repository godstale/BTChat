# BTChat
BT chatting between arduino and android. (supports IoT feature)


아두이노와 블루투스로 연결해서 간단한 채팅을 할 수 있도록 작성한 앱입니다. IoT를 위한 HTTP Request 기능도 포함하고 있습니다.

좌측상단 블루투스 연결 버튼을 눌러 기기를 검색한 후 페어링을 할 수 있습니다. 페어링이 되면 이후 블루투스로 들어오는 문자열을 화면에 표시해줍니다. 간단한 채팅이나 로그 모니터링 용으로 사용할 수 있습니다.
IoT 지원을 위해 특정 문자열이 들어올 경우 자동으로 HTTP Request를 보낼 수 있도록 작성되었습니다. 아래와 같은 문자열을 인식합니다.

Whenever BTChat find message like : thingspeak:key=xxx&field1=xxx[*]

Automatically sends a HTTP Request : http://184.106.153.149/update?key=xxx&field1=xxx

