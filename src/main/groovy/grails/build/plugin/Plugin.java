package grails.build.plugin;

import java.io.File;
import java.util.Set;

public interface Plugin {
    String getName();
    String getVersion();
    boolean isSourcePlugin();
    File getPluginDirectory();
    Set<String> getLoadedAfter();
    Set<String> getLoadedBefore();
    Set<File> getRuntimeClasspath();
    void setRuntimeClasspath(Set<File> paths);
    
    void doWithWebDescriptor(Object xml);
}
