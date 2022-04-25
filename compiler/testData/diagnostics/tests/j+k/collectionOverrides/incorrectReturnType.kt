// FILE: CommentedMap.java
public interface CommentedMap<K, V> extends java.util.Map<K, V> {}
// FILE: MultiMap.java
public interface MultiMap<K, V> extends java.Map<K, V> {}
// FILE: Profile.java
public interface Profile extends MultiMap<String, Profile.Section>, CommentedMap<String, Profile.Section> {}
// FILE: BasicMultiMap.java
public abstract class BasicMultiMap<K, V> implements MultiMap<K, V> {}
// FILE: BasicProfile.java
public abstract class BasicProfile extends CommonMultiMap<String, Profile.Section> implements Profile {}

// FILE: main.kt

abstract class MyProfile : BasicProfile()
