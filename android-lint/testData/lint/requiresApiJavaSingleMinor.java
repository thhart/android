package androidx.annotation;

import android.os.Build;

public @interface RequiresApi {
    int value();
}

class RequiresApiTest {
    public void test() {
        <error descr="Call requires API level 35.2 (current min is 1): `requires_352`">requires<caret>_352</error>();
    }

    @RequiresApi(35*100_000+2) // Use constant in VERSION_CODES when available
    public void requires_352() {
    }
}