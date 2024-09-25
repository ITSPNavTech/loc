# KDAS Test Location Application
1. History
	1.0.0	 최초 배포

	1.0.1 	minSDK 버전 을 30에서 28로 수정
		ejml 라이브러리를 최신 버전으로 업데이트 후 관련 코드 수정
		android.gms 라이브러리 최신 버전으로 업데이트

        1.2.0  라이브러리 수정 (사용 라이브러리 축소)
		위치 정확도 향상 (고속 환경)
                implementation 'com.google.guava:guava:31.1-jre'
    		implementation 'com.google.protobuf:protobuf-java:3.19.3'
    		implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    		implementation 'org.ejml:ejml-simple:0.43.1'
    		implementation 'com.google.android.gms:play-services-location:21.1.0'
    		implementation 'com.squareup.retrofit2:converter-scalars:2.9.0'
    		debugImplementation files('libs/itsploc-1.2.0.aar')

2. 라이브러리 사용 방법

	가. 인투스페이스로부터 API 사용을 위한 Key를 발급
		techinfo@intospace.co.kr

	나. 프로젝트의 app\libs 에 itsploc-X.X.X.aar 라이브러리 추가.

	다. 어플리케이션의 build.gradle 파일의 dependencies에 라이브러리 추가
   	 	implementation 'com.google.guava:guava:31.1-jre'
    		implementation 'com.google.protobuf:protobuf-java:3.19.3'
    		implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    		implementation 'org.ejml:ejml-simple:0.43.1'
    		implementation 'com.google.android.gms:play-services-location:21.1.0'
    		implementation 'com.squareup.retrofit2:converter-scalars:2.9.0'
    		debugImplementation files('libs/itsploc-1.2.0.aar')


	라. permission 추가.
		1) 어플리케이션의 AndroidManifest.xml에 다음과 같이 권한 추가
	    		<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
	    		<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
	    		<uses-permission android:name="android.permission.INTERNET" />

	마. 어플리케이션에 API 추가
		예제 코드 상의 주석 중 INFO 주석을 참고
	바. 예제 프로그램 위치
		https://github.com/ITSPNavTech/loc
****