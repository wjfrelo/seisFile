package edu.sc.seis.seisFile.syncFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import edu.sc.seis.seisFile.SeisFileException;

/**
 * Compares two sync files, creating 3 psuedo-sync files. 
 * <ul><li>In A and In B</li>
 * <li>In A and Not In B</li>
 * <li>Not In A and In B</li></ul>
 * 
 * @author crotwell
 * 
 */
public class SyncFileCompare {

    public SyncFileCompare(SyncFile a, SyncFile b) {
        this.a = a;
        this.b = b;
        process();
    }

    void process() {
        String now = SyncLine.dateToString(new Date());
        inAinB = new SyncFile(a.dccName + " " + b.getDccName(), now, new String[] {"sync compare inAinB"});
        notAinB = new SyncFile(a.dccName + " " + b.getDccName(), now, new String[] {"sync compare notAinB"});
        inAnotB = new SyncFile(a.dccName + " " + b.getDccName(), now, new String[] {"sync compare inAnotB"});
        List<SyncLine> aLines = a.getSyncLines();
        Collections.sort(aLines);
        List<SyncLine> bLines = b.getSyncLines();
        Collections.sort(bLines);
        Iterator<SyncLine> bIterator = bLines.iterator();
        Iterator<SyncLine> aIterator = aLines.iterator();
        SyncLine[] out = new SyncLine[] {null, null};
        while (out[0] != null || out[1] != null || aIterator.hasNext() || bIterator.hasNext()) {
            if (out[0] == null && aIterator.hasNext()) {
                out[0] = aIterator.next();
            }
            if (out[1] == null && bIterator.hasNext()) {
                out[1] = bIterator.next();
            }
            out = processItem(out[0], out[1], inAinB, notAinB, inAnotB);
        }
    }

    static SyncLine[] processItem(SyncLine aLine, SyncLine bLine, SyncFile inAinB, SyncFile notAinB, SyncFile inAnotB) {
        System.out.println("Test: "+aLine+" "+bLine);
        if (aLine != null && bLine != null) {
            if (aLine.getEndTime().before(bLine.getStartTime())) {
                // both sorted, so know only in A
                inAnotB.addLine(aLine, true);
                aLine = null;
                System.out.println("put A into "+inAnotB.getDccName());
            } else if (bLine.getEndTime().before(aLine.getStartTime())) {
                // both sorted, so know bL only in B
                notAinB.addLine(bLine, true);
                bLine = null;
                System.out.println("put B into "+notAinB.getDccName());
            } else {
                System.out.println("Overlap");
                // some time overlap between aL and bL
                if (aLine.getStartTime().equals(bLine.getStartTime()) && aLine.getEndTime().equals(bLine.getEndTime())) {
                    // same
                    inAinB.addLine(aLine);
                    aLine = null;
                    bLine = null;
                } else if (aLine.getStartTime().equals(bLine.getStartTime())) {
                    // only differ in endtime
                    if (aLine.getEndTime().before(bLine.getEndTime())) {
                        inAinB.addLine(aLine);
                        bLine = bLine.split(aLine.getEndTime())[1];
                        aLine = null;
                    } else {
                        inAinB.addLine(bLine);
                        aLine = aLine.split(bLine.getEndTime())[1];
                        bLine = null;
                    }
                } else if (aLine.getStartTime().before(bLine.getStartTime())) {
                    // part of a before B
                    SyncLine[] split = aLine.split(bLine.getStartTime());
                    inAnotB.addLine(split[0]);
                    if (split.length != 1) {
                        return processItem(split[1], bLine, inAinB, notAinB, inAnotB);
                    }
                } else if (bLine.getStartTime().before(aLine.getStartTime())) {
                    // part of b before a, swap order and reprocess
                    SyncLine[] tmp = processItem(bLine, aLine, inAinB, inAnotB, notAinB);
                    return new SyncLine[] {tmp[1], tmp[0]};
                }
            }
        } else if (aLine != null) {
            // bLine is null, so only in A
            inAnotB.addLine(aLine);
            aLine = null;
        } else if (bLine != null) {
            // aLine is null, so only in B
            notAinB.addLine(bLine);
            bLine = null;
        }
        return new SyncLine[] {aLine, bLine};
    }

    public SyncFile getA() {
        return a;
    }

    public SyncFile getB() {
        return b;
    }

    public SyncFile getInAinB() {
        return inAinB;
    }

    public SyncFile getNotAinB() {
        return notAinB;
    }

    public SyncFile getInAnotB() {
        return inAnotB;
    }

    SyncFile a;

    SyncFile b;

    SyncFile inAinB;

    SyncFile notAinB;

    SyncFile inAnotB;
    
    public static void main(String[] args) throws IOException, SeisFileException {
        if (args.length != 2) {
            System.err.println("Usage: syncFileCompare file1.sync file2.sync");
            return;
        }
        SyncFile file1 = SyncFile.load(new File(args[0]));
        SyncFile file2 = SyncFile.load(new File(args[1]));
        SyncFileCompare sfc = new SyncFileCompare(file1, file2);
        sfc.getInAinB().saveToFile("in_"+args[0]+"_in_"+args[1]+".sync");
        sfc.getNotAinB().saveToFile("not_"+args[0]+"_in_"+args[1]+".sync");
        sfc.getInAnotB().saveToFile("in_"+args[0]+"_not_"+args[1]+".sync");
        System.out.println("Done: A: "+file1.getSyncLines().size()+" B: "+file2.getSyncLines().size()+" inAinB: "+sfc.getInAinB().getSyncLines().size()+" notAinB: "+sfc.getNotAinB().getSyncLines().size()+" inAnotB: "+sfc.getInAnotB().getSyncLines().size());
    }
}
