# Keep commonmark extension node classes (accessed via instanceof checks)
-keep class org.commonmark.** { *; }

# JLaTeXMath loads fonts/symbol tables by resource name at runtime
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
