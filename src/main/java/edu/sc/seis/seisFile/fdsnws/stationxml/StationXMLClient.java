package edu.sc.seis.seisFile.fdsnws.stationxml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.seisFile.fdsnws.stationxml.InstrumentSensitivity;
import edu.sc.seis.seisFile.fdsnws.stationxml.Network;
import edu.sc.seis.seisFile.fdsnws.stationxml.NetworkIterator;
import edu.sc.seis.seisFile.fdsnws.stationxml.Response;
import edu.sc.seis.seisFile.fdsnws.stationxml.Station;
import edu.sc.seis.seisFile.fdsnws.stationxml.StationIterator;
import edu.sc.seis.seisFile.fdsnws.stationxml.StationXMLException;
import edu.sc.seis.seisFile.fdsnws.stationxml.StationXMLTagNames;

public class StationXMLClient {

    public static void main(String[] args) throws XMLStreamException, StationXMLException, IOException {
        String urlString = "http://service.iris.edu/fdsnws/station/1/query?network=IU&station=SNZO&channel=BHZ&level=channel";
        if (! (args.length == 0 || (args.length == 2 && args[0].equals("-u")))) {
            System.out.println("Usage: stationxmlclient -u url");
            System.out.println("       stationxmlclient -u "+urlString);
            return;
        }
        if (args.length > 1) {
            urlString = args[1];
        }
        URL url = new URL(urlString);
        System.out.println("URL: "+urlString);
        URLConnection urlConn = url.openConnection();
        if (urlConn instanceof HttpURLConnection) {
            HttpURLConnection conn = (HttpURLConnection)urlConn;
            if (conn.getResponseCode() == 204) {
                // 204 means query was ok, but nothing found
                System.out.println("No Data!");
                return;
            } else if (conn.getResponseCode() != 200) {
                String out = "";
                BufferedReader errReader = null;
                try {
                    errReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    for (String line; (line = errReader.readLine()) != null;) {
                        out += line + "\n";
                    }
                } finally {
                    if (errReader != null)
                        try {
                            errReader.close();
                            conn.disconnect();
                        } catch(IOException e) {
                            throw e;
                        }
                }
                System.err.println("Error in connection with url: " + url);
                System.err.println(out);
                return;
            }
        }
        // likely not an error in the http layer, so assume XML is returned
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLEventReader r = factory.createXMLEventReader(url.toString(), urlConn.getInputStream());
        XMLEvent e = r.peek();
        while (!e.isStartElement()) {
            e = r.nextEvent(); // eat this one
            e = r.peek(); // peek at the next
        }
        System.out.println("FDSNStationXML");
        FDSNStationXML fdsnStationXML = new FDSNStationXML(r);
        if (!fdsnStationXML.checkSchemaVersion()) {
            System.out.println("");
            System.out.println("WARNING: XmlSchema of this document does not match this code, results may be incorrect.");
            System.out.println("XmlSchema (code): " + StationXMLTagNames.CURRENT_SCHEMALOCATION_VERSION);
            System.out.println("");
        }
        System.out.println("XmlSchema: " + fdsnStationXML.getXmlSchemaLocation());
        System.out.println("Source: " + fdsnStationXML.getSource());
        System.out.println("Sender: " + fdsnStationXML.getSender());
        System.out.println("Module: " + fdsnStationXML.getModule());
        System.out.println("ModuleUri: " + fdsnStationXML.getModuleUri());
        System.out.println("SentDate: " + fdsnStationXML.getCreated());
        NetworkIterator it = fdsnStationXML.getNetworks();
        while (it.hasNext()) {
            Network n = it.next();
            System.out.println("Network: " + n.getCode() + " " + n.getDescription() + " " + n.getStartDate() + " "
                    + n.getEndDate());
            StationIterator sit = n.getStations();
            while (sit.hasNext()) {
                Station s = sit.next();
                System.out.println("  Station: " + n.getCode() + "." + s.getCode() + " " + "  " + s.getStartDate()
                        + " to " + s.getEndDate()+" "+s.getSelectedNumChannels()+" of "+s.getTotalNumChannels());
                for (Comment comment : s.getCommentList()) {
                //    System.out.println("          " + comment);
                }
                List<Channel> chanList = s.getChannelList();
                for (Channel channel : chanList) {
                    System.out.println("      Channel: " + channel.getLocCode() + "." + channel.getCode() + "  "
                            + channel.getStartDate() + " to " + channel.getEndDate());
                    for (Comment comment : channel.getCommentList()) {
                        System.out.println("          " + comment);
                    }
                    if (channel.getEquipment() != null) {
                        System.out.println("Equipment: "+channel.getEquipment().getType());
                    }
                    Response resp = channel.getResponse();
                    if (resp != null) {
                        float overallGain = 1;
                        for (ResponseStage stage : resp.getResponseStageList()) {
                            System.out.print("          Resp " +stage.getNumber() 
                                             + " " +  stage.getResponseItem().getClass().getSimpleName()
                                             +" " + (stage.getResourceId()==null?"":stage.getResourceId()));
                            if (stage.getResponseItem() != null) {
                                System.out.print(" " + stage.getResponseItem().getInputUnits() + " "
                                        + stage.getResponseItem().getOutputUnits());
                            }
                            if (stage.getStageSensitivity() != null) {
                                System.out.print(" " + stage.getStageSensitivity().getSensitivityValue());
                                if (stage.getNumber() != 0) {
                                    overallGain *= stage.getStageSensitivity().getSensitivityValue();
                                }
                            }
                            System.out.println();
                        }
                        InstrumentSensitivity instSens = resp.getInstrumentSensitivity();
                        System.out.println("          Overall Gain: " + overallGain + "  Inst Sense: "
                                + instSens.getSensitivityValue() + " " + instSens.getInputUnits()+" to "+instSens.getOutputUnits());
                    }
                }
            }
        }
        fdsnStationXML.closeReader();
    }
}