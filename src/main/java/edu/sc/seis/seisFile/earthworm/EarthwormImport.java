package edu.sc.seis.seisFile.earthworm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;


public class EarthwormImport {
    
    public EarthwormImport(InputStream in) {
        this.in = in;
    }
    
    public EarthwormMessage nextMessage() throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int index = 0;
        int nextByte = in.read();
        boolean isEscapedByte = false;
        while(nextByte != EarthwormExport.STX) {
            nextByte = in.read();
        }
        // got a start, now read data
        nextByte = in.read();
        if (nextByte == EarthwormExport.ESC) {
            isEscapedByte = true;
            nextByte = in.read();
        }
        
        while( isEscapedByte || nextByte != EarthwormExport.ETX) {
            buffer[index] = (byte)nextByte;
            index++;
            isEscapedByte = false;
            nextByte = in.read();
            if (nextByte == EarthwormExport.ESC) {
                isEscapedByte = true;
                nextByte = in.read();
            }
        }
        byte[] tbBuf = new byte[index];
        System.arraycopy(buffer, 0, tbBuf, 0, tbBuf.length);
        return new EarthwormMessage(tbBuf);
    }
    
    InputStream in;
    
    int BUFFER_SIZE = 4096*2;
    
    /** just for testing, prints a message for each tracebuf received. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: earthwormImpor host port");
            return;
        }
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final String heartbeatMessage = "heartbeat";
        final int heartbeatSeconds = 10;
        final int institution = 2;
        final int module = 99;
        HashMap<String, Double> lastTimeMap = new HashMap<String, Double>();
        try {
            Socket s = new Socket(host, port);
            final BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            final EarthwormEscapeOutputStream outStream = new EarthwormEscapeOutputStream(new BufferedOutputStream(s.getOutputStream()));
            EarthwormHeartbeater heartbeater = new EarthwormHeartbeater(null, heartbeatSeconds, heartbeatMessage, institution, module);

            heartbeater.setOutStream(outStream);
            heartbeater.heartbeat();
            
            EarthwormImport ewImport = new EarthwormImport(in);
            while(true) {
                EarthwormMessage message;
                try {
                    message = ewImport.nextMessage();
                    if (message.getMessageType() == EarthwormMessage.MESSAGE_TYPE_TRACEBUF2) {
                        TraceBuf2 traceBuf2 = new TraceBuf2(message.getData());
                        String key = traceBuf2.formatNSLCCodes();
                        if (lastTimeMap.containsKey(key)) {
                            if (Math.abs(traceBuf2.getStartTime() - lastTimeMap.get(key)) > 1/traceBuf2.getSampleRate()) {
                                System.out.println("GAP: "+(traceBuf2.getStartTime() - lastTimeMap.get(key)));
                            }
                        }
                        lastTimeMap.put(key, traceBuf2.getPredictedNextStartTime());
                        System.out.println("TraceBuf: "+traceBuf2);
                    } else if (message.getMessageType() == EarthwormMessage.MESSAGE_TYPE_HEARTBEAT) {
                        System.out.println("Heartbeat received: "+new String(message.data));
                    }
                    Thread.sleep(1);
                } catch(IOException e) {
                    e.printStackTrace();
                    heartbeater.setOutStream(null);
                    outStream.close();
                    break;
                }
            }
        } catch (IOException e) {
            throw new IOException("Unable to bind to '"+host+"' at port "+port, e);
        }
    }
}
