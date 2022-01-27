// FILE: GroupedConstants.java
public class GroupedConstants {
    public static class MobileOsVendor {
        public static final String  ANDROID = "google";

        public static final String  IOS = "apple";
    }
}

// FILE: GroupedConstantsV2.java
public class GroupedConstantsV2 extends GroupedConstants {
    public static class MobileFormFactor {
        public static final String PHONE = "phones_iphones";

        public static final String TABLET = "tablets_ipads";
    }
}

// FILE: main.kt
fun main() {
    GroupedConstantsV2.<!UNRESOLVED_REFERENCE!>MobileOsVendor<!>.ANDROID // unresolved
    GroupedConstantsV2.MobileFormFactor.PHONE
}