package deltix.qsrv.solgen.cpp;

import deltix.qsrv.solgen.SolgenUtils;
import deltix.qsrv.solgen.base.Project;
import deltix.qsrv.solgen.base.Property;
import deltix.qsrv.solgen.base.PropertyFactory;
import deltix.qsrv.solgen.base.Source;
import deltix.qsrv.solgen.java.GradleProject;
import deltix.util.io.BasicIOUtil;
import deltix.util.io.GUID;
import deltix.util.io.Home;
import deltix.util.io.UncheckedIOException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class VsProject implements Project {

    public static final String PROJECT_TYPE = "vs2015";

    public static final Property CPP_PROJECT_ROOT = PropertyFactory.create(
        "cpp.project.root",
        "Directory, where project will be stored",
        true,
        SolgenUtils::isValidPath,
        SolgenUtils.getDefaultSamplesDirectory().resolve("cpp").toString()
    );
    public static final Property CPP_PROJECT_NAME = PropertyFactory.create(
        "cpp.project.name",
        "Project name, like google-collections",
        false,
        GradleProject::isValidName,
        "sample-project"
    );

    public static final List<Property> PROPERTIES = Collections.unmodifiableList(Arrays.asList(
        CPP_PROJECT_ROOT, CPP_PROJECT_NAME
    ));

    private static final String VCXPROJ_TEMPLATE = "project.vcxproj-template";
    private static final String VCXPROJ_FILTER_TEMPLATE = "project.vcxproj.filters-template";

    private static final String FILES_LIST_PROP = "cpp.filesList";
    private static final String FILTER_FILES_PROP = "cpp.filterFiles";
    private static final String FILTERS_PROP = "cpp.filters";

    private final Path root;
    private final Path srcPath;
    private final Path libPath;
    private final String name;
    private final List<Path> sources = new ArrayList<>();
    private final Map<String, String> templateParams = new HashMap<>();

    private final List<Path> scripts = new ArrayList<>();

    private final Properties properties = new Properties();

    public VsProject(Properties properties) {
        this(Paths.get(properties.getProperty(CPP_PROJECT_ROOT.getName())),
            properties.getProperty(CPP_PROJECT_NAME.getName()));
    }

    public VsProject(Path root, String name) {
        this.root = root;
        this.srcPath = root.resolve("src");
        this.libPath = root.resolve("dxapi");
        this.name = name;
        templateParams.put(CPP_PROJECT_ROOT.getName(), this.name);
    }

    @Override
    public Path getSourcesRoot() {
        return srcPath;
    }

    @Override
    public Path getResourcesRoot() {
        return null;
    }

    @Override
    public Path getProjectRoot() {
        return root;
    }

    @Override
    public Path getLibsRoot() {
        return libPath;
    }

    @Override
    public List<Path> getScripts() {
        return scripts;
    }

    @Override
    public void markAsScript(Path path) {
        scripts.add(path);
    }

    @Override
    public void setProjectProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    @Override
    public void createSkeleton() throws IOException {
        copyLibs();
    }

    @Override
    public void addSource(Source source) {
        // todo: how to use default from interface?
        Path path = getSourcesRoot().resolve(source.getRelativePath());
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (PrintWriter writer = new PrintWriter(path.toFile())) {
            writer.print(source.getContent());
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }

        sources.add(path);
    }

    @Override
    public void flush() throws IOException {
        templateParams.put("cpp.project.guid", "{" + new GUID().toString() + "}");
        templateParams.put(FILES_LIST_PROP, CppCodegenUtils.projectFiles(root, sources));
        templateParams.put(FILTER_FILES_PROP, CppCodegenUtils.filterFiles(root, sources));
        templateParams.put(FILTERS_PROP, CppCodegenUtils.filters(root, sources));

        String projFile = SolgenUtils.readTemplateFromClassPath(
            this.getClass().getPackage(), VCXPROJ_TEMPLATE, templateParams
        );
        try (PrintWriter writer = new PrintWriter(root.resolve(name + ".vcxproj").toFile())) {
            writer.print(projFile);
        }

        String filtersFile = SolgenUtils.readTemplateFromClassPath(
            this.getClass().getPackage(), VCXPROJ_FILTER_TEMPLATE, templateParams
        );
        try (PrintWriter writer = new PrintWriter(root.resolve(name + ".vcxproj.filters").toFile())) {
            writer.print(filtersFile);
        }
    }

    public String getName() {
        return name;
    }

    private void copyLibs() {
        try {
            BasicIOUtil.copyDirectory(
                Home.getFile("cpp", "dxapi", "windows", "include", "dxapi"),
                libPath.resolve("include").resolve("dxapi").toFile(),
                true, true, null, null
            );
            BasicIOUtil.copyDirectory(
                Home.getFile("cpp", "dxapi", "windows", "x64"),
                libPath.resolve("lib").toFile(),
                true, true, null, null
            );
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
