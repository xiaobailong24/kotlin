// !DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_PARAMETER
// RENDER_PACKAGE: test
// WITH_STDLIB
// JSR305_GLOBAL_REPORT: warn

// FILE: test/NonNullApi.java
package test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

@Target({ElementType.TYPE, ElementType.PACKAGE})
@Nonnull
@TypeQualifierDefault({ElementType.METHOD, ElementType.PARAMETER})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface NonNullApi {
}

// FILE: test/package-info.java
@NonNullApi
package test;

// FILE: test/Provider.java
package test;

import javax.annotation.Nullable;

public interface Provider<T> {
    T get();
    @Nullable T getOrNull();
    T getOrElse(T defaultValue);
}

// FILE: main.kt
import test.Provider

@Suppress("UNUSED_VARIABLE")
fun f(versionCode: Provider<Int?>, versionName: Provider<String?>) {
    val codeGet: Int? = versionCode.get()
    val codeGetOrNull: Int? = versionCode.orNull
    val codeGetOrElse: Int? = versionCode.getOrElse(1)
    // False positive: it is necessary, otherwise it doesn't compile with TYPE_MISMATCH.
    val codeGetOrElseNonNull: Int = versionCode.getOrElse(1)!!
    // False positive: T is Int? so `null` is a valid parameter to getOrElse.
    val codeGetOrElseNull: Int? = versionCode.getOrElse(null)

    // This is here to show that the issue exists not only on primitive types.
    val nameGet: String? = versionName.get()
    val nameGetOrNull: String? = versionName.orNull
    val nameGetOrElse: String? = versionName.getOrElse("")
    // False positive: it is necessary, otherwise it doesn't compile with TYPE_MISMATCH.
    val nameGetOrElseNonNull: String = versionName.getOrElse("")!!
    // False positive: T is String? so `null` is a valid parameter to getOrElse.
    val nameGetOrElseNull: String? = versionName.getOrElse(null)
}
