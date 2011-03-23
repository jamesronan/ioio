package ioio.lib;

import static ioio.api.SpiChannel.SPI_125K;
import static ioio.api.SpiChannel.SPI_142K;
import static ioio.api.SpiChannel.SPI_166K;
import static ioio.api.SpiChannel.SPI_1M;
import static ioio.api.SpiChannel.SPI_1_3M;
import static ioio.api.SpiChannel.SPI_200K;
import static ioio.api.SpiChannel.SPI_250K;
import static ioio.api.SpiChannel.SPI_2M;
import static ioio.api.SpiChannel.SPI_2_2M;
import static ioio.api.SpiChannel.SPI_2_6M;
import static ioio.api.SpiChannel.SPI_31K;
import static ioio.api.SpiChannel.SPI_333K;
import static ioio.api.SpiChannel.SPI_35K;
import static ioio.api.SpiChannel.SPI_3_2M;
import static ioio.api.SpiChannel.SPI_41K;
import static ioio.api.SpiChannel.SPI_4M;
import static ioio.api.SpiChannel.SPI_500K;
import static ioio.api.SpiChannel.SPI_50K;
import static ioio.api.SpiChannel.SPI_571K;
import static ioio.api.SpiChannel.SPI_5_3M;
import static ioio.api.SpiChannel.SPI_62K;
import static ioio.api.SpiChannel.SPI_666K;
import static ioio.api.SpiChannel.SPI_800K;
import static ioio.api.SpiChannel.SPI_83K;
import static ioio.api.SpiChannel.SPI_8M;
import static ioio.api.SpiChannel.SPI_OFF;
import ioio.api.DigitalInput;
import ioio.api.DigitalOutput;
import ioio.api.PeripheralException.ConnectionLostException;

import java.nio.ByteBuffer;

import android.util.SparseArray;

/**
 * 
 * @author arshan
 *
 */
public class SpiMaster {
    public static final SparseArray<Integer> scaleDivMap;

    static {
        scaleDivMap = new SparseArray<Integer>();
        scaleDivMap.append(SPI_OFF, 0);
        scaleDivMap.append(SPI_31K, (3 << 3) | 7);
        scaleDivMap.append(SPI_35K, (3 << 3) | 6);
        scaleDivMap.append(SPI_41K, (3 << 3) | 5);
        scaleDivMap.append(SPI_50K, (3 << 3) | 4);
        scaleDivMap.append(SPI_62K, (3 << 3) | 3);
        scaleDivMap.append(SPI_83K, (3 << 3) | 2);
        scaleDivMap.append(SPI_125K, (2 << 3) | 7);
        scaleDivMap.append(SPI_142K, (2 << 3) | 6);
        scaleDivMap.append(SPI_166K, (2 << 3) | 5);
        scaleDivMap.append(SPI_200K, (2 << 3) | 4);
        scaleDivMap.append(SPI_250K, (2 << 3) | 3);
        scaleDivMap.append(SPI_333K, (2 << 3) | 2);
        scaleDivMap.append(SPI_500K, (1 << 3) | 7);
        scaleDivMap.append(SPI_571K, (1 << 3) | 6);
        scaleDivMap.append(SPI_666K, (1 << 3) | 5);
        scaleDivMap.append(SPI_800K, (1 << 3) | 4);
        scaleDivMap.append(SPI_1M, (1 << 3) | 3);
        scaleDivMap.append(SPI_1_3M, (1 << 3) | 2);
        scaleDivMap.append(SPI_2M, 7);
        scaleDivMap.append(SPI_2_2M, 6);
        scaleDivMap.append(SPI_2_6M, 5);
        scaleDivMap.append(SPI_3_2M, 4);
        scaleDivMap.append(SPI_4M,  3);
        scaleDivMap.append(SPI_5_3M, 2);
        scaleDivMap.append(SPI_8M, 1);
    }

    private DigitalInput miso;
    private DigitalOutput mosi;
    private DigitalOutput clk;
    private boolean isOpen = false;

    private IOIOImpl controller;
    
    private IOIOPacket configureMaster;
    private IOIOPacket setMiso;
    private IOIOPacket setMosi;
    private IOIOPacket setClk;
    
    // TODO incorporate the module allocator
    private int spiNum = 0;
    
    public SpiMaster(
            DigitalInput miso, 
            DigitalOutput mosi, 
            DigitalOutput clk, 
            IOIOImpl controller){
        this.miso = miso;
        this.mosi = mosi;
        this.clk = clk;
        this.controller = controller;
        
    }
    
    public void init(int speed) throws ConnectionLostException {
     
        // seems like this could be wrapped into a single request.
        setMiso = new IOIOPacket(
                Constants.SPI_SET_PIN, 
                new byte[]{
                        (byte) miso.getPinNumber(),
                        (byte) (0x14 & spiNum)
                }
                );
        setMosi = new IOIOPacket(
                Constants.SPI_SET_PIN, 
                new byte[]{
                        (byte) mosi.getPinNumber(),
                        (byte) (0x10 & spiNum)
                }
                );
        setClk = new IOIOPacket(
                Constants.SPI_SET_PIN, 
                new byte[]{
                        (byte) clk.getPinNumber(),
                        (byte) (0x1C & spiNum)
                }
                );
     
        controller.sendPacket(setMiso);
        controller.sendPacket(setMosi);
        controller.sendPacket(setClk);
        
        configureMaster = new IOIOPacket(
                Constants.SPI_CONFIGURE_MASTER,
                new byte[]{
                        (byte) (spiNum << 5 | scaleDivMap.get(speed)),
                        (byte) 0 // TODO, expose these in builder and decide on default
                }
                );
        
        controller.sendPacket(configureMaster);
        
        isOpen = true;
    }
    
    public void send(
            int select, 
            ByteBuffer send, 
            ByteBuffer receive, 
            int rxOffset) 
    throws ConnectionLostException {
        byte[] data;
       
        // if the rx is offset then the byte cnt is not same as total
        int total = Math.max(send.remaining(), receive.remaining() + rxOffset);
        boolean rx = rxOffset > 0;
        boolean tx = send.remaining() < total;
        data = new byte[send.remaining() + 2 + (rx?1:0) + (tx?1:0)];
        data[0] = (byte)(spiNum << 6 | select);
        data[1] = (byte)((rx?0x40:0) & (tx?0x80:0) & (total-1));
        int pos = 2;
        if (tx) {
            data[pos++] = (byte)send.remaining();
        }
        if (rx) {
            data[pos++] = (byte)receive.remaining();
        }
        System.arraycopy(send.slice().array(), 0, data, pos, send.remaining());
        
        IOIOPacket pkt = new IOIOPacket(
                Constants.SPI_MASTER_REQUEST,
                data
        );
        
        controller.sendPacket(pkt);
    }
    
    public boolean isOpen() {
        return isOpen;
    }
    
    public void close() {
        isOpen = false;
    }
    
    public int getNum() {
        return spiNum;
    }
}