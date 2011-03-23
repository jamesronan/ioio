/*
 * TODO(arshan): What is the copyright info? all the files need it
 */
package ioio.lib;

import ioio.api.AnalogInput;
import ioio.api.DigitalInput;
import ioio.api.DigitalInputMode;
import ioio.api.DigitalOutput;
import ioio.api.DigitalOutputMode;
import ioio.api.IOIOLib;
import ioio.api.PeripheralException.ConnectionLostException;
import ioio.api.PeripheralException.InvalidOperationException;
import ioio.api.PeripheralException.OperationAbortedException;
import ioio.api.PeripheralException.OutOfResourceException;
import ioio.api.PwmOutput;
import ioio.api.Uart;

import java.io.IOException;
import java.net.BindException;
import java.net.SocketException;

import android.accounts.OperationCanceledException;

/**
 * High level controller and vars to the IOIO.
 * 
 * TODO(arshan) : link here to web assets
 * 
 * @author arshan
 */
public class IOIOImpl implements IOIOLib {
   
    // pin 0 - onboard LED; pins 1-48 physical external pins
    // TODO(arshan): this has to keep config info on the pins too.
    private ModuleAllocator myPins = getNewPinAllocation();

    private ModuleAllocator getNewPinAllocation() {
        return new ModuleAllocator(49);
    }
    
    private final PacketFramerRegistry framerRegistry = new PacketFramerRegistry();

    private static final int CONNECT_WAIT_TIME_MS = 100;

    // TODO(arshan): lets move this into something like IoioConfig
    private static final DigitalInputMode DEFAULT_DIGITAL_INPUT_MODE = DigitalInputMode.FLOATING;

	private final IOIOConnection ioioConnection;

	private boolean abortConnection = false;

	ListenerManager listeners;

	public IOIOImpl(IOIOConnection ioio_connection, ListenerManager listeners) {
	    this.ioioConnection = ioio_connection;
	    this.listeners = listeners;
	}

	public IOIOImpl(ListenerManager listeners) {
	    this(new IOIOConnection(listeners), listeners);
	}

	public IOIOImpl() {
	    this(new ListenerManager());
	}

    public boolean isConnected() {
		return ioioConnection.isVerified();
	}

	// queue an outgoing packet
	public void sendPacket(IOIOPacket pkt) throws ConnectionLostException {
		ioioConnection.sendToIOIO(pkt);
	}

	/**
	 * Blocking call that will not return until connected.
	 * hmm. this must throw an exception at some point, else very un-androidy
	 * @throws SocketException
	 * @throws OperationCanceledException if {@link #abortConnection()} is called
	 */
    public void waitForConnect() throws OperationAbortedException, SocketException {

	    abortConnection = false;

	    if (!isConnected()) {
	        try {
                ioioConnection.start(framerRegistry);
                myPins = getNewPinAllocation();
            } catch (BindException e) {
                e.printStackTrace();
                throw new SocketException("BindException: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                throw new SocketException("IOException: " + e.getMessage());
            }
	    }
	    // TODO(birmiwal): make this better
	    while (!isConnected() && !abortConnection) {
	        sleep(CONNECT_WAIT_TIME_MS);
	    }

	    if (!isConnected()) {
	        throw new OperationAbortedException("operation aborted while in connect()");
	    }

	    sleep(CONNECT_WAIT_TIME_MS);
	}

	@Override
	public void abortConnection() {
	    abortConnection = true;
	    ioioConnection.disconnect();
	}

	@Override
	public void softReset() throws ConnectionLostException {
		ioioConnection.sendToIOIO(Constants.SOFT_RESET_PACKET);
		listeners.disconnectListeners();
	}

	@Override
    public void hardReset() throws ConnectionLostException {
		// request a reset
		ioioConnection.sendToIOIO(Constants.HARD_RESET_PACKET);
	}

	public void registerListener(IOIOPacketListener listener){
        listeners.registerListener(listener);
    }
	
	public void unregisterListener(IOIOPacketListener listener){
        listeners.unregisterListener(listener);
    }

    public void disconnect() {
	    abortConnection = true;
	    ioioConnection.disconnect();
	}

    public DigitalInput openDigitalInput(int pin) throws ConnectionLostException, InvalidOperationException {
		return openDigitalInput(pin, DEFAULT_DIGITAL_INPUT_MODE);
	}

    public DigitalOutput openDigitalOutput(int pin, boolean startValue) throws ConnectionLostException, InvalidOperationException {
        return openDigitalOutput(pin, startValue, DigitalOutputMode.NORMAL);
    }

    public DigitalOutput openDigitalOutput(int pin, boolean startValue, DigitalOutputMode mode) throws ConnectionLostException, InvalidOperationException {
        return new IOIODigitalOutput(this, framerRegistry, pin, mode, startValue);
    }

	public AnalogInput openAnalogInput(int pin) throws ConnectionLostException, InvalidOperationException {
		return new IOIOAnalogInput(this, pin, framerRegistry);
	}

	/**
	 * @return the next available uart module
	 */
	private int nextAvailableUart() {
		return 0; // support just the one for now.
	}

    public void reservePin(int pin) throws InvalidOperationException {
        boolean allocated = myPins.requestAllocate(pin);
        if (!allocated) {
            throw new InvalidOperationException("Pin " + pin + " already open");
        }
    }

    public void releasePin(int pin) {
        myPins.releaseModule(pin);
    }

    @Override
    public DigitalInput openDigitalInput(int pin, DigitalInputMode mode)
            throws ConnectionLostException, InvalidOperationException {
        return new IOIODigitalInput(this, framerRegistry, pin, mode);
    }

    
    @Override
    public PwmOutput openPwmOutput(int pin, int freqHz) throws OutOfResourceException,
            ConnectionLostException, InvalidOperationException {
        return new IOIOPwmOutput(this, openDigitalOutput(pin, false), freqHz);
    }

    @Override
    public PwmOutput openPwmOutput(DigitalOutput pin, int freqHz) throws OutOfResourceException,
            ConnectionLostException, InvalidOperationException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uart openUart(int rx, int tx, int baud, int parity, int stopbits)
            throws ConnectionLostException, InvalidOperationException {
        return null;
    }

    
    @Override
    public Uart openUart(DigitalInput rx, DigitalOutput tx, int baud, int parity, int stopbits)
            throws ConnectionLostException, InvalidOperationException {
        // TODO Auto-generated method stub
        return null;
    }

   
    public PacketFramerRegistry getFramerRegistry() {
        return this.framerRegistry;
    }
   
    @Override
    public IOIOSpi openSpi(int miso, int mosi, int clk, int select, int speed) 
    throws ConnectionLostException, InvalidOperationException {      
        return openSpi(
                openDigitalInput(miso),
                openDigitalOutput(mosi, false),
                openDigitalOutput(clk, false),
                openDigitalOutput(select, false),
                speed
                );
    }
    
    @Override
    public IOIOSpi openSpi(DigitalInput miso, DigitalOutput mosi, DigitalOutput clk,
            DigitalOutput select, int speed) 
    throws ConnectionLostException, InvalidOperationException{
       return new IOIOSpi(miso, mosi, clk, select, speed, this);
    }

    public void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

  

}