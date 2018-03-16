#include <SoftwareSerial.h>

const int tonesSize = 8;
const int fingerPins[] = { 0,1,2,3,4,6,7,8 };
const int tones[] = {
	432,
	523,
	587,
	659,
	698,
	784,
	880,
	988
};
const int speakerPin = 5;
const int diodePin = 9;

SoftwareSerial BTserial(11, 10); 
                      //RX TX     
                  //(HC-06: TX RX)
int i;
int currentlyPressed = -1;
int previouslyPressed = -1;
int enableSpeaker = 1;
long lastPingTime = 0;
char lastPing = 2;

void setup() {
	BTserial.begin(9600);
	for (i = 0; i < tonesSize; i++) {
		pinMode(fingerPins[i], INPUT_PULLUP);
	}
	pinMode(speakerPin, OUTPUT);
  pinMode(diodePin, OUTPUT);
}


void loop() {  
  if(BTserial.available() > 0){
    lastPingTime = millis();
    lastPing = BTserial.read();
  }
  if(millis()-lastPingTime < 2000L && lastPing!=2){
    enableSpeaker=0;
    digitalWrite(diodePin,LOW);
  }else{
    enableSpeaker=1;
    digitalWrite(diodePin,128);
  }
  
	currentlyPressed = -1;  
	for (i = 0; i < tonesSize; i++) {
		if (digitalRead(fingerPins[i]) == LOW) {
			currentlyPressed = i;
			break;
		}
	}  
	if ( currentlyPressed >= 0 && 
	      currentlyPressed != previouslyPressed ) {
		if(enableSpeaker) {
		  tone(speakerPin, tones[currentlyPressed]);
		}		
		String str;
		str = String(currentlyPressed);
    str.concat(";");
		char b[3];
		str.toCharArray(b, 3);
		BTserial.write(b);
    
	}else if( currentlyPressed == -1 ){
		noTone(speakerPin);
	}
  previouslyPressed = currentlyPressed;
  
	delay(100);
}
