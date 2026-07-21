 exclusiveContent {
            forRepository {
                maven {
                    url("https://dl.cloudsmith.io/basic/test-bba0/test/maven/")

                    credentials {
                        // Pulls credentials from your system environment variables
                        username = System.getenv("CLOUDSMITH_USERNAME")
                        password = System.getenv("CLOUDSMITH_API_KEY")
                    }
                }
            }
            filter {
                includeModule("com.facebook.react", "react-android")
                includeModule("com.facebook.react", "hermes-android")
            }
        }
