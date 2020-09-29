package deltix.qsrv.dtb.store.tools.shell;

import deltix.qsrv.dtb.fs.local.*;
import deltix.qsrv.dtb.fs.pub.*;
import deltix.qsrv.dtb.store.raw.*;
import deltix.qsrv.util.cmd.AbstractShellEx;
import deltix.util.cmdline.*;
import deltix.util.time.*;
import java.io.*;

/**
 *
 */
public class DTBShell extends AbstractShellEx {
    private final DiagPrinter               diagPrinter = 
        new DiagPrinter ();
    
    private int                             maxPathDispLength = 30;    
    private final AbstractFileSystem        fs = FSFactory.getLocalFS();
    private AbstractPath                    path;
    
    @Override
    protected String                        getPrompt () {
        if (path == null)
            return (super.getPrompt ());
        
        StringBuilder   sb = new StringBuilder (path.toString ());
        int             len = sb.length ();
        
        if (len > maxPathDispLength) 
            sb.replace (0, 3 + len - maxPathDispLength, "...");        
            
        if (RawFolder.isTSFolder (path) || RawTSF.isTSFile (path))
            sb.append (" >>> ");
        else
            sb.append (" ==> ");
        
        return (sb.toString ());
    }    
    
    public static String                    formatTime (long t) {
        if (t == Long.MIN_VALUE)
            return ("------------ MIN ------------");
        
        if (t == Long.MAX_VALUE)
            return ("------------ MAX ------------");
        
        return (
            GMT.formatDateTime (t / 1000000) + "." + 
            String.format ("%09d", t % 1000000000L)
        );
    }
    
    public void                             cmd_show (String arg) throws IOException {
        AbstractPath        f = getPath (arg);
        
        if (f == null)
            System.out.println ("cd <...> first");
        else if (RawFolder.isTSFolder (f)) {
            RawFolder   folder = new RawFolder ();
            
            folder.setPath (f);
            folder.readIndex (diagPrinter);
            
            int         n = folder.getNumChildren ();
            System.out.println ("TS Folder " + f + ":");
            System.out.println ("    Format #:        " + folder.getFormatVersion ());
            System.out.println ("    Revision:        " + folder.getVersion ());
            System.out.println ("    Next child id:   " + folder.getNextChildId ());
            System.out.println ("    # Children:      " + n);
            System.out.println ("    # Entities:      " + folder.getNumEntities ());

            for (int i = 0; i < n; i++) {
                RawFolderEntry child = folder.getChild(i);
                cmd_show(arg + "\\" + child.getName());
            }
        }   
        else if (RawTSF.isTSFile (f)) {
            RawTSF      tsf = new RawTSF ();
            
            tsf.setPath (f);
            tsf.readIndex (diagPrinter);
            
            System.out.println ("TS File " + f + ":");
            System.out.printf ("    Length:          %,d B\n", tsf.getPhysicalLength ());
            System.out.printf ("    Format #:        %d\n", tsf.getFormatVersion ());
            System.out.printf ("    Revision:        %d\n", tsf.getVersion ());
            System.out.printf ("    # Blocks:        %,d\n", tsf.getNumEntities ());
            System.out.printf ("    Min Timestamp:   %s\n",
                formatTime (tsf.getMinTimestamp ())
            ); 
            System.out.printf ("    Max Timestamp:   %s\n",
                formatTime (tsf.getMaxTimestamp ())
            );

            int entities = tsf.getNumEntities ();
            for (int i = 0; i < entities; i++) {
                RawDataBlock block = tsf.getBlock(i);
                System.out.printf ("    Block #:        %,d\n", i);
                System.out.printf ("        Length:     %,d\n", block.getDataLength());
                System.out.printf ("        Start time: %s\n", formatTime(block.getFirstTimestamp()));
                System.out.printf ("        Last  time: %s\n", formatTime (block.getLastTimestamp()));

            }
        }
    }
        
    public void                             cmd_ls (String arg) throws IOException {
        AbstractPath        f = getPath (arg);
        
        if (f == null)
            System.out.println ("cd <...> first");
        else if (RawFolder.isTSFolder (f)) {
            RawFolder   folder = new RawFolder ();
            
            folder.setPath (f);
            folder.readIndex (diagPrinter);
            
            int         n = folder.getNumChildren ();
            
            for (int ii = 0; ii < n; ii++) {
                RawFolderEntry      child = folder.getChild (ii);
                String              tstext = formatTime (child.getStartTimestamp ());
                
                if (child.isFile ())
                    System.out.printf (
                        "#%,5d: %s      %s      %,7d KB\n",
                        ii,
                        child.getName (),
                        tstext,
                        f.append (child.getName ()).length () >> 10
                    );
                else
                    System.out.printf (
                        "#%,5d: %s      %s\n",
                        ii,
                        child.getName (),
                        tstext
                    );
            }
        }
        else if (f.isFolder ()) {
            String []           names = path.listFolder ();

            for (String s : names) {
                System.out.println (s);
            }
        }
    }


    @Override
    protected boolean doCommand(String key, String args) throws Exception {
        if ("show".equalsIgnoreCase(key)) {
            cmd_show(args);
        }
        return super.doCommand(key, args);
    }

    private AbstractPath                    getPath (String pathText) {
        if (pathText == null)
            return (path);
    
        if (fs.isAbsolutePath (pathText)) 
            return (fs.createPath (pathText));        
        else {
            if (path == null) {
                error ("No current path (cd <absolute path> first)", 1);
                return (null);
            }
            
            return (path.append (pathText));
        }
    }
    
    public void                             cmd_cd (String pathText) throws IOException {
        AbstractPath        newPath = getPath (pathText);
        
        if (!newPath.exists ()) {
            error (newPath + " does not exist.", 1);
            return;
        }

        path = newPath;                   
    }
    
    public void                             cmd_verify (String arg) throws IOException {
        AbstractPath        f = getPath (arg);
        
        Verifier            vfr = new Verifier (diagPrinter, VerificationMode.COMPLETE);
        
        vfr.verifyObject (f);
    }
    
    public                                  DTBShell (String[] args) {
        super (args);
    }

    public static void                      main (String [] args) 
        throws Exception 
    {
        new DTBShell (args).start ();
    }
}
