package org.openhab.binding.wifiled.handler;

import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static org.openhab.binding.wifiled.handler.ClassicWiFiLEDDriver.bytesToHex;

/**
 * Abstract WiFi LED driver.
 *
 * @author Osman Basha - Initial contribution
 * @author Stefan Endrullis &lt;stefan@endrullis.de&gt;
 */
public abstract class AbstractWiFiLEDDriver {

    public enum Protocol {
        LD382,
        LD382A;
    }

    public enum Driver {
        CLASSIC,
        FADING
    }

    public static final Integer DEFAULT_PORT = 5577;

    protected static final int DEFAULT_SOCKET_TIMEOUT = 5000;

    protected Logger logger = LoggerFactory.getLogger(AbstractWiFiLEDDriver.class);
    protected String host;
    protected int port;
    protected Protocol protocol;

    public AbstractWiFiLEDDriver(String host, int port, Protocol protocol) {
        this.host = host;
        this.port = port;
        this.protocol = protocol;
    }

    public abstract void setColor(HSBType color) throws IOException;

    public abstract void setBrightness(PercentType brightness) throws IOException;

    public abstract void incBrightness(int step) throws IOException;

    public void decBrightness(int step) throws IOException {
        incBrightness(-step);
    }

    public abstract void setWhite(PercentType white) throws IOException;

    public abstract void incWhite(int step) throws IOException;

    public void decWhite(int step) throws IOException {
        incWhite(-step);
    }

    public abstract void setProgram(StringType program) throws IOException;

    public abstract void setProgramSpeed(PercentType speed) throws IOException;

    public abstract void incProgramSpeed(int step) throws IOException;

    public void decProgramSpeed(int step) throws IOException {
        incProgramSpeed(-step);
    }

    public abstract void setPower(OnOffType command) throws IOException;

    public void init() throws IOException {
        getLEDState();
    }

    public abstract LEDStateDTO getLEDStateDTO() throws IOException;

    protected synchronized LEDState getLEDState() throws IOException {
        try (Socket socket = new Socket(host, port)) {
            logger.debug("Connected to '{}'", socket);

            socket.setSoTimeout(DEFAULT_SOCKET_TIMEOUT);

            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());

            byte[] data = { (byte) 0x81, (byte) 0x8A, (byte) 0x8B, (byte) 0x96 };
            outputStream.write(data);
            logger.debug("Data sent: '{}'", bytesToHex(data));

            byte[] statusBytes = new byte[14];
            inputStream.readFully(statusBytes);
            logger.debug("Data read: '{}'", bytesToHex(statusBytes));

            // Example response (14 Bytes):
            // 0x81 0x04 0x23 0x26 0x21 0x10 0x45 0x00 0x00 0x00 0x03 0x00 0x00 0x47
            // ..........^--- On/Off.........R....G....B....WW..
            // ...............^-- PGM...^---SPEED...............

            int state = statusBytes[2] & 0xFF; // On/Off
            int program = statusBytes[3] & 0xFF;
            int programSpeed = statusBytes[5] & 0xFF;

            int red = statusBytes[6] & 0xFF;
            int green = statusBytes[7] & 0xFF;
            int blue = statusBytes[8] & 0xFF;
            int white = statusBytes[9] & 0xFF;

            logger.debug("RGBW: {},{},{},{}", red, green, blue, white);

            return new LEDState(state, program, programSpeed, red, green, blue, white);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    protected void sendRaw(byte[] data) throws IOException {
        sendRaw(data, 100);
    }

    protected synchronized void sendRaw(byte[] data, int delay) throws IOException {
        try (Socket socket = new Socket(host, port)) {
            logger.debug("Connected to '{}'", socket);

            socket.setSoTimeout(DEFAULT_SOCKET_TIMEOUT);

            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            sendRaw(data, outputStream);

            if (delay > 0) {
                Thread.sleep(delay);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    protected void sendRaw(byte[] data, DataOutputStream outputStream) throws IOException {
        byte[] dataWithCS;

        // append 0x0F (if dev.type LD382A)
        if (protocol.equals(Protocol.LD382A)) {
            dataWithCS = new byte[data.length + 2];
            dataWithCS[dataWithCS.length - 2] = 0x0F;
        } else {
            dataWithCS = new byte[data.length + 1];
        }

        // append checksum
        System.arraycopy(data, 0, dataWithCS, 0, data.length);
        int cs = 0;
        for (int i = 0; i < dataWithCS.length - 1; i++) {
            cs += dataWithCS[i];
        }
        cs = cs & 0xFF;
        dataWithCS[dataWithCS.length - 1] = (byte) cs;

        outputStream.write(dataWithCS);
        logger.debug("RAW data sent: '{}'", bytesToHex(dataWithCS));
    }

}
