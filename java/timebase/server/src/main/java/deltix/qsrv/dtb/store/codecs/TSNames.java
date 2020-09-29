package deltix.qsrv.dtb.store.codecs;

/**
 *
 */
public class TSNames {
    public static final String      FOLDER_NAME_PREFIX = "u";
    
    public static final String      FILE_NAME_PREFIX = "z";
    
    public static final String      TMP_PREFIX = "tmp.";
    
    public static final int         TMP_PREFIX_LENGTH = TMP_PREFIX.length ();
    
    public static final String      SAVE_PREFIX = "save.";
    
    public static final String      TMP_ROOT_SUBFOLDER_NAME = TMP_PREFIX + "rsf";
    
    public static final String      SYM_REGISTRY_NAME = "symbols.dat";
    public static final String      ROOT_PROPS_NAME = "config.properties";
    
    public static final String      INDEX_NAME = "index.dat";                   
    
    public static String            buildFileName (int id) {
        return (String.format ("%s%04x.dat", FILE_NAME_PREFIX, id));
    }
    
    public static String            buildFolderName (int id) {
        return (String.format ("%s%04x", FOLDER_NAME_PREFIX, id));
    }

    public static boolean           isTSFileName (String name) {
        return (name.startsWith (FILE_NAME_PREFIX));
    }

    public static boolean           isTSFolder (String name) {
        return (name.startsWith (FOLDER_NAME_PREFIX));
    }

    public static int               getEID(String name) {
        if (isTSFileName(name)) {
            String number = name.substring(TSNames.FILE_NAME_PREFIX.length()).replace(".dat", "");
            return Integer.parseInt(number, 16);
        } else if (isTSFolder(name)) {
            String number = name.substring(TSNames.FOLDER_NAME_PREFIX.length());
            return Integer.parseInt(number, 16);
        }
        throw new IllegalArgumentException(name + " is neither folder or file");
    }
}
