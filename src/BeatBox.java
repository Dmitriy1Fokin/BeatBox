import com.sun.deploy.panel.JavaPanel;

import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

public class BeatBox {
    JFrame theFrame;
    JPanel mainPanel;
    JList incomingList;
    JTextField userMessage;
    ArrayList<JCheckBox> checkBoxList;
    int nextNum;
    Vector<String> listVector = new Vector<String>();
    String userName;
    ObjectOutputStream out;
    ObjectInputStream in;
    HashMap<String, boolean[]> otherSeqsMap = new HashMap<String, boolean[]>();

    Sequencer sequencer;
    Sequence sequence;
    Sequence mySequence = null;
    Track track;

    String[] instrumentNames = {"Bass Drum", "Closed Hi-Hat",
            "Open Hi-Hat", "Acoustic Snare", "Crash Cymbal", "Hand Clap",
            "High Tom", "Hi Bongo", "Maracas", "Whistle", "Low Conga",
            "Cowbell", "Vibraslap", "Low-mid Tom", "High Agogo", "Open Hi Conga"};
    int[] instruments = {35,42,46,38,49,39,50,60,70,72,64,56,58,47,67,63};

    public static void main (String[]args){
        new BeatBox().startUp("Dima");
    }

    public void startUp(String name){
        userName = name;
        try{
            Socket sock = new Socket("127.0.0.1", 4242);
            out = new ObjectOutputStream(sock.getOutputStream());
            in = new ObjectInputStream(sock.getInputStream());
            Thread remote = new Thread(new RemoteReader());
            remote.start();
        }catch (Exception ex){
            System.out.println("couldn't connect");
        }
        setUpMidi();
        buildGUI();
    }

    public void buildGUI(){
        theFrame = new JFrame("Cybet BeatBox");
        theFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        BorderLayout layout = new BorderLayout();
        JPanel background = new JPanel(layout);
        background.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        checkBoxList = new ArrayList<JCheckBox>();

        Box buttonBox = new Box(BoxLayout.Y_AXIS);

        JButton start = new JButton("Start");
        start.addActionListener(new MyStartListener());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(new MyStopListener());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo Up");
        upTempo.addActionListener(new MyUpTempoListener());
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Tempo down");
        downTempo.addActionListener(new MyDownTempoListener());
        buttonBox.add(downTempo);

        JButton serializeit = new JButton("Save");
        serializeit.addActionListener(new MySaveListener());
        buttonBox.add(serializeit);

        JButton restore = new JButton("Open");
        restore.addActionListener(new MyReadListener());
        buttonBox.add(restore);

        JButton sendIt = new JButton("send it");
        sendIt.addActionListener(new MySendListener());
        buttonBox.add(sendIt);

        userMessage = new JTextField();
        buttonBox.add(userMessage);

        incomingList = new JList();
        incomingList.addListSelectionListener(new MyListSelectionListener());
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incomingList);
        buttonBox.add(theList);
        incomingList.setListData(listVector);

        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for (int i=0; i<16; i++){
            nameBox.add(new Label(instrumentNames[i]));
        }

        background.add(BorderLayout.EAST, buttonBox);
        background.add(BorderLayout.WEST, nameBox);

        theFrame.getContentPane().add(background);
        GridLayout grid = new GridLayout(16,16);
        grid.setVgap(1);
        grid.setHgap(2);
        mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER, mainPanel);

        for (int i=0; i<256; i++){
            JCheckBox c = new JCheckBox();
            c.setSelected(false);
            checkBoxList.add(c);
            mainPanel.add(c);
        }

        theFrame.setBounds(50,50,300,300);
        theFrame.pack();
        theFrame.setVisible(true);
    }

    public void setUpMidi(){
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ, 4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void buildTrackAndStart(){
        ArrayList<Integer> trackList =  null;

        sequence.deleteTrack(track);
        track = sequence.createTrack();

        for (int i=0; i<16; i++){
            trackList = new ArrayList<Integer>();

            for (int j=0; j<16; j++){
                JCheckBox jc = (JCheckBox) checkBoxList.get(j+(16*i));
                if (jc.isSelected()){
                    int key = instruments[i];
                    trackList.add(new Integer(key));
                }else{
                    trackList.add(null);
                }
            }

            makeTracks(trackList);
        }
        track.add(makeEvent(192,9,1,0,15));
        try {
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);
            sequencer.start();
            sequencer.setTempoInBPM(120);
        }catch (Exception e){
            e.printStackTrace();}
    }

    public class MyStartListener implements ActionListener{
        public void actionPerformed(ActionEvent ev){
            buildTrackAndStart();
        }
    }

    public class MyStopListener implements ActionListener{
        public  void actionPerformed(ActionEvent ev){
            sequencer.stop();
        }
    }

    public class MyUpTempoListener implements ActionListener{
        public void actionPerformed(ActionEvent ev){
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float)(tempoFactor*1.03));
        }
    }

    public class MyDownTempoListener implements ActionListener{
        public void actionPerformed(ActionEvent ev){
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float)(tempoFactor*.97));
        }
    }

    public class MySaveListener implements ActionListener{
        public void actionPerformed (ActionEvent ev){
            JFileChooser fileSave = new JFileChooser();
            fileSave.showSaveDialog(theFrame);
            saveFile(fileSave.getSelectedFile());
        }
    }

    private void saveFile(File file){
        try{
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            for (int i=0; i<256; i++){
                JCheckBox check = (JCheckBox) checkBoxList.get(i);
                if (check.isSelected()){
                    writer.write("1 ");
                }else {
                    writer.write("0 ");
                }
            }
            writer.close();
        }catch (IOException ex){
            System.out.println("couldn't write");
            ex.printStackTrace();
        }
    }

    public class MyReadListener implements ActionListener{
        public void actionPerformed (ActionEvent ev){
            sequencer.stop();
            JFileChooser fileOpen = new JFileChooser();
            fileOpen.showOpenDialog(theFrame);
            loadFile(fileOpen.getSelectedFile());
        }
    }

    private void loadFile(File file){
        try{
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String CheckboxState = null;
            CheckboxState = reader.readLine();
            makeCheckbox(CheckboxState);
            reader.close();
        }catch (IOException ex){
            System.out.println("couldn't read");
            ex.printStackTrace();
        }
    }

    private void makeCheckbox(String lineToParse){
        String[] result = lineToParse.split(" ");
        //System.out.println("result lenght = " + result.length);
        for (int i=0; i<256; i++){
            JCheckBox check = (JCheckBox) checkBoxList.get(i);
            if (result[i].equals("1")){
                //System.out.println("result[" + i + "]" + " = " + result[i]);
                check.setSelected(true);
            }else {
                check.setSelected(false);
            }
            //sequencer.stop();
            buildTrackAndStart();
        }
    }

    public class MySendListener implements ActionListener{
        public void actionPerformed(ActionEvent a){
            boolean[] checkboxState = new boolean[256];
            for (int i=0; i<256; i++){
                JCheckBox check = (JCheckBox) checkBoxList.get(i);
                if (check.isSelected()){
                    checkboxState[i]= true;
                }
            }
            String messageToSend = null;
            try {
                out.writeObject(userName + nextNum++ + ": " + userMessage.getText());
                out.writeObject(checkboxState);
            }catch (Exception ex){
                System.out.println("Sorry dude. Could not sent it to the server.");
            }
            userMessage.setText("");
        }
    }

    public class MyListSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent le){
            if (!le.getValueIsAdjusting()){
                String seleced = (String) incomingList.getSelectedValue();
                if(seleced != null){
                    boolean[] selectedState = (boolean[]) otherSeqsMap.get(seleced);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }
    }

    public class RemoteReader implements Runnable{
        boolean[] checkboxState = null;
        String nameToShow = null;
        Object obj = null;
        public void run(){
            try{
                while((obj=in.readObject()) != null){
                    System.out.println("got an object from server");
                    System.out.println(obj.getClass());
                    String nameToShow = (String) obj;
                    checkboxState = (boolean[]) in.readObject();
                    otherSeqsMap.put(nameToShow, checkboxState);
                    listVector.add(nameToShow);
                    incomingList.setListData(listVector);
                }
            }catch (Exception ex){
                ex.printStackTrace();
            }
        }
    }

    public class MyPlayMineListener implements ActionListener{
        public void actionPerformed(ActionEvent ev){
            if(mySequence != null){
                sequence = mySequence;
            }
        }
    }

    public void changeSequence(boolean[] checkboxState){
        for(int i=0; i<256; i++){
            JCheckBox check = (JCheckBox) checkBoxList.get(i);
            if (checkboxState[i]){
                check.setSelected(true);
            }else {
                check.setSelected(false);
            }
        }
    }

    public void makeTracks(ArrayList list){
        Iterator it = list.iterator();
        for (int i=0; i<16; i++){
            Integer num = (Integer) it.next();
            if (num !=null){
                int numKey = num.intValue();
                track.add(makeEvent(144,9,numKey,100,i));
                track.add(makeEvent(128,9,numKey,100,i+1));
            }
        }
    }

    public MidiEvent makeEvent(int comd, int chan, int one, int two, int tick){
        MidiEvent event = null;
        try {
            ShortMessage a = new ShortMessage();
            a.setMessage(comd,chan,one,two);
            event = new MidiEvent(a, tick);
        }catch (Exception e){
            e.printStackTrace();
        }
        return event;
    }

}
