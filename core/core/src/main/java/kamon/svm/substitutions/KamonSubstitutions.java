package kamon.svm.substitutions;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;


@TargetClass(className = "kamon.lib.org.jctools.util.UnsafeRefArrayAccess")
final class Target_kamon_lib_org_jctools_util_UnsafeRefArrayAccess {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.ArrayIndexShift, declClass = Object[].class)
    public static int REF_ELEMENT_SHIFT;
}

public class KamonSubstitutions {

}
