fun box1(): String {
    "1234".apply {
        try {
        } finally {
            ::fu1
        }
    }
    return "OK"
}