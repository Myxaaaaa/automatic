plugins {
	id("com.android.application")
	kotlin("android")
}

android {
	namespace = "com.example.notifyforwarder"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.example.notifyforwarder"
		minSdk = 26
		targetSdk = 34
		versionCode = 1
		versionName = "1.0.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		getByName("debug") {
			isMinifyEnabled = false
		}
		getByName("release") {
			isMinifyEnabled = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	buildFeatures {
		viewBinding = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("com.google.android.material:material:1.12.0")
	implementation("androidx.activity:activity-ktx:1.9.3")
	implementation("androidx.recyclerview:recyclerview:1.3.2")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("com.squareup.moshi:moshi:1.15.1")
	implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

	// QR scanning
	implementation("com.journeyapps:zxing-android-embedded:4.3.0")
	implementation("com.google.zxing:core:3.5.3")
}


