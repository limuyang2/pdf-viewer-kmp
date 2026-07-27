-keep class io.github.limuyang2.pdf.core.internal.AndroidPdfiumNative {
    native <methods>;
}

-keep class io.github.limuyang2.pdf.core.internal.AndroidNativePdfSearchMatch {
    <init>(int, int, double[]);
}

-keep class io.github.limuyang2.pdf.core.internal.AndroidNativePdfLink {
    <init>(double[], int, int, double[], byte[]);
}
