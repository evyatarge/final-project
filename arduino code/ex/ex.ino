// =================== Global Variables ===================
int numOfSen = 3; // the number of sensor
int delayVal = 500; // the delay time in ms (1000ms = 1sec)

int senPinA[] = {A0,A1,A2}; // the number of the pins
int valSen[] = {0,0,0}; // need to read the analog data
int mapVal[] = {0,0,0}; // need to mapping the data from [0,1024] to [0,255]
// ========================================================

// in Arduino you must write 2 function: I.setup, II.loop
// setup -> Occurs once at the start of the run
// loop -> Always running in circles
void setup()
{
   Serial.begin(9600); // open the serial output data on 9600 baud
}

void loop()
{
  for (int i=0; i<numOfSen; i++)
    {
      valSen[i] = analogRead(senPinA[i]); // read data from the Sensors
      valSen[i] = constrain(valSen[i], 600, 900); // set limits
      mapVal[i] = map(valSen[i], 600, 900, 255, 0); // mapping the data
    } 
    
    for (int i=0; i<numOfSen-1; i++) // print the data to the Serial (Rx,Tx)
    {
      Serial.print(mapVal[i]);
      Serial.print(" ");
    }
    Serial.println(mapVal[numOfSen-1]);
  
    delay(delayVal);
}
