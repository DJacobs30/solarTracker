import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinDigitalMultipurpose;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.PinPullResistance;

public class SolarTracking {

    // Set up the GPIO instance variables
    private GpioController gpio;

    private GpioPinDigitalOutput directionA;
    private GpioPinDigitalOutput stepA;
    private GpioPinDigitalOutput enableA;

    private GpioPinDigitalOutput directionB;
    private GpioPinDigitalOutput stepB;
    private GpioPinDigitalOutput enableB;

    private GpioPinDigitalOutput adcCS;
    private GpioPinDigitalOutput adcCLK;
    private GpioPinDigitalMultipurpose adcDIO;

    private GpioPinDigitalOutput[] motor1;
    private GpioPinDigitalOutput[] motor2;

    private short maxLight;
    private int xPos;
    private int yPos;
    private short temp;
    private boolean isMax = False;
    
    // Construct the gpio
    public SolarTracking()
    {
        gpio = GpioFactory.getInstance();
        initGpio();
    }


        // GPIO SETUP


    private void initGpio()
    {
        // Initialize the GPIO
        System.out.println("Setting Up GPIO");

        // Set up pins
        directionA = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03, "Direction Pin A", PinState.LOW);
        stepA = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, "Step Pin A", PinState.LOW);
        enableA = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "Enable Pin A", PinState.LOW);

        directionB = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_23, "Direction Pin B", PinState.LOW);
        stepB = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_24, "Step Pin B", PinState.LOW);
        enableB = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_25, "Enable Pin B", PinState.LOW);

        // ADC Pins
		adcCS = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, "Chip Select");
		adcCLK = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Clock");
		adcDIO = gpio.provisionDigitalMultipurposePin(RaspiPin.GPIO_02, "Input/Output", PinMode.DIGITAL_OUTPUT);

        // Set Shutdown States
        directionA.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);
        stepA.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);
        enableA.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);

        directionB.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);
        stepB.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);
        enableB.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);

        adcCS.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);
        adcCLK.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);
        adcDIO.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_DOWN);

        // Put the stepper pins into lists
        motor1 = new GpioPinDigitalOutput[]{directionA, stepA, enableA};
        motor2 = new GpioPinDigitalOutput[]{directionB, stepB, enableB};
    }

    private void stepMotor(String direction, int steps)
    {
        // Method to control the movement of the stepper motor
        int stepSize = 0;
        int stepSpeed = 0;
        String motor = "";

        // Figure out which hat to talk to by the direction
        if (direction == "up") {

            stepSize = 1000;
            stepSpeed = 1;
            directionA.high();
            motor = "a";

        } else if (direction == "down") {
            
            stepSize = 1000;
            stepSpeed = 1;
            directionA.low();
            motor = "a";

        } else if (direction == "right") {
            
            stepSize = 20;
            stepSpeed = 10;
            directionB.high();
            motor = "b";

        } else if (direction == "left") {
            
            stepSize = 20;
            stepSpeed = 10;
            directionB.low();
            motor = "b";

        }

        for (int step = 0; step < steps; step++)
        {
            for (int n = 0; n < stepSize; n++)
            {
                try 
                {
                    if (motor == "a") // North South Motor
                    {
                        stepA.high();
                        Thread.sleep(stepSpeed);
                        stepA.low();
                        Thread.sleep(stepSpeed);

                    } else if (motor == "b") // East West Motor
                    {
                        stepB.high();
                        Thread.sleep(stepSpeed);
                        stepB.low();
                        Thread.sleep(stepSpeed);
                    }
                    
                } catch (Exception e) {System.out.println(e);}
            }
        }
    }


        // LIGHT SENSOR


    private short getADCResult(int channel) 
    {
		short dat1 = 0, dat2 = 0;
        long delay = 2;

        // Prepare ACD_DIO for MUX addess configuration 
        adcDIO.setMode(PinMode.DIGITAL_OUTPUT);
        
        // Start converstaion        
        adcCS.low();

        // MUX Start bit to setup MUX address (Multiplexer configuration)
        adcCLK.low();
        adcDIO.high();
        adcCLK.high();

        // MUX SGL/-DIF git to setup Sigle-Ended channel type
        adcCLK.low();
        adcDIO.high();
        adcCLK.high();

        // MUX ODD/SIGN bit to setup
        adcCLK.low();
        if(channel==0){
            adcDIO.low();  // analog input in Channel #0
        }else{
            adcDIO.high();  // analog input in Channel #1
        }
        adcCLK.high();

        // Keep the clock going to settle the MUX address
        adcCLK.low();

        // Read MSB byte
        adcDIO.setMode(PinMode.DIGITAL_INPUT);
        for (byte i = 0; i < 8; i++) {
            adcCLK.high();
            adcCLK.low();
            dat1 = (short) ((dat1 << 1) | adcDIO.getState().getValue());
        }
        // Read LSB byte
        for (byte i = 0; i < 8; i++) {
            dat2 = (short) (dat2 | (adcDIO.getState().getValue() << i));
            adcCLK.high();
            adcCLK.low();
        }
        // End of conversation.
        adcCS.high();

        // If valid reading MSF == LSF
        return dat1 == dat2 ? dat1 : 0;
	}


        // INITIAL LOCATION


    private void scanEW()
    {
        // Method to scan 180 degrees of sun
        // Move 90 to the right
        stepMotor("right", 15);

        // Move 180 to the left checking the light every step
        for (int n = 0; n < 30; n++)
        {
            stepMotor("left", 1);
            short currentLight = getADCResult(0);

            // set the highest light value, then check if the next is higher
            if (n == 0) {
                maxLight = currentLight;
                xPos = n;
            } else if (currentLight > maxLight) {
                maxLight = currentLight;
                xPos = n;
            }
        }
        // Return to the highest point
        stepMotor("right", 30 - xPos);
    }

    private void scanNS()
    {
        // Method to scan 45 degrees of sun
        // Move 20 up
        stepMotor("up", 10);

        // Move 45 down checking light every step
        for (int n = 0; n < 20; n++)
        {
            stepMotor("down", 1);
            short currentLight = getADCResult(0);

            // set the highest light value, then check if the next is higher
            if (n == 0) {
                maxLight = currentLight;
                yPos = n;
            } else if (currentLight > maxLight) {
                maxLight = currentLight;
                yPos = n;
            }
        }
        // Return to the highest point
        stepMotor("up", 20 - yPos);
    }


        // ADJUSTMENT MOVEMENT


    private String adjust()
    {
        // Method to adjust the positiono to follow the sun
        // Get the current Light Check
        maxLight = getADCResult(0);
        String dir = "mid";

        // Check left
        stepMotor("left", 1);
        temp = getADCResult(0);
        if (temp > maxLight) {maxLight = temp; dir = "left";}
        stepMotor("right", 1);

        // Check Up
        stepMotor("up", 1);
        temp = getADCResult(0);
        if (temp > maxLight) {maxLight = temp; dir = "up";}
        stepMotor("right", 1);

        // Check Right
        stepMotor("right", 1);
        temp = getADCResult(0);
        if (temp > maxLight) {maxLight = temp; dir = "right";}
        stepMotor("left", 1);

        // Check Down
        stepMotor("down", 1);
        temp = getADCResult(0);
        if (temp > maxLight) {maxLight = temp; dir = "down";}
        stepMotor("up", 1);

        // Return Direction
        return dir;
    }

    private void testAdjust()
    {
        // Method to loop the adjust function
        boolean isMoving = True;
        String dir;

        while (isMoving)
        {
            // Check the direction with the best light
            dir = adjust();

            // Go to that direction
            if (dir == "left") {stepMotor("left", 1);}
            else if (dir == "up") {stepMotor("up", 1);}
            else if (dir == "right") {stepMotor("right", 1);}
            else if (dir == "down") {stepMotor("down", 1);}
            else if (dir == "mid") {isMoving = false;}
        }
    }


        // MAIN


    public static void main(String[] args) throws InterruptedException
    {
        SolarTracking active = new SolarTracking();

        active.scanEW();
        /*
        active.scanNS();

        Thread.sleep(30000);

        active.testAdjust();
        */
    }
}
