package deltix.qsrv.solgen.java.samples;

import deltix.anvil.util.StringUtil;
import deltix.qsrv.solgen.SolgenUtils;
import deltix.qsrv.solgen.base.*;
import deltix.qsrv.solgen.java.JavaSampleFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class WriteStreamSample implements Sample {

    public static final Property STREAM_KEY = PropertyFactory.create(
            "timebase.stream",
            "TimeBase stream key.",
            true,
            StringUtil::isNotEmpty
    );
    public static final Property CLASS_NAME = PropertyFactory.create(
            "java.samples.writestream.className",
            "Short class name like \"SampleClass\" (without package).",
            false,
            JavaSamplesUtil::isValidClassName,
            "WriteStream"
    );
    public static final Property PACKAGE_NAME = PropertyFactory.create(
            "java.samples.writestream.packageName",
            "Package name like deltix.timebase.sample",
            false,
            JavaSamplesUtil::isValidPackageName,
            "deltix.timebase.sample"
    );

    public static final List<Property> PROPERTIES = List.of(STREAM_KEY);

    private static final String WRITE_STREAM_TEMPLATE = "WriteStream.java-template";

    private final Source writeStreamSource;

    public WriteStreamSample(Properties properties) {
        this(properties.getProperty(JavaSampleFactory.TB_URL.getName()),
                properties.getProperty(STREAM_KEY.getName()),
                properties.getProperty(PACKAGE_NAME.getName(), PACKAGE_NAME.getDefaultValue()),
                properties.getProperty(CLASS_NAME.getName(), CLASS_NAME.getDefaultValue()));
    }

    public WriteStreamSample(String tbUrl, String key, String packageName, String className) {
        Map<String, String> params = new HashMap<>();
        params.put(JavaSampleFactory.TB_URL.getName(), tbUrl);
        params.put(STREAM_KEY.getName(), key);
        params.put(PACKAGE_NAME.getName(), packageName);
        params.put(CLASS_NAME.getName(), className);

        writeStreamSource = new StringSource(packageName.replace('.', '/') + "/" + className + ".java",
                SolgenUtils.readTemplateFromClassPath(this.getClass().getPackage(), WRITE_STREAM_TEMPLATE, params));
    }

    @Override
    public void addToProject(Project project) {
        project.addSource(writeStreamSource);
    }
}
