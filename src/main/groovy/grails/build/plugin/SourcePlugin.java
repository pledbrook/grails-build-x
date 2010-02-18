package grails.build.plugin;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: pal20
 * Date: 18-Feb-2010
 * Time: 15:46:46
 * To change this template use File | Settings | File Templates.
 */
public interface SourcePlugin extends Plugin {
    File getBuildConfigFile();
}
