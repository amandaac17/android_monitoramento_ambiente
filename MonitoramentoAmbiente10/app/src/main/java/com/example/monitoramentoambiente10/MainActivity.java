package com.example.monitoramentoambiente10;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import br.ufma.lsdi.cddl.CDDL;
import br.ufma.lsdi.cddl.Connection;
import br.ufma.lsdi.cddl.ConnectionFactory;
import br.ufma.lsdi.cddl.components.MOUUID;
import br.ufma.lsdi.cddl.components.TechnologyID;
import br.ufma.lsdi.cddl.listeners.IConnectionListener;
import br.ufma.lsdi.cddl.listeners.ISubscriberListener;
import br.ufma.lsdi.cddl.message.CommandMessage;
import br.ufma.lsdi.cddl.message.Message;
import br.ufma.lsdi.cddl.message.QueryResponseMessage;
import br.ufma.lsdi.cddl.message.SensorDataMessage;
import br.ufma.lsdi.cddl.message.ServiceInformationMessage;
import br.ufma.lsdi.cddl.network.ConnectionImpl;
import br.ufma.lsdi.cddl.pubsub.Publisher;
import br.ufma.lsdi.cddl.pubsub.PublisherFactory;
import br.ufma.lsdi.cddl.pubsub.Subscriber;
import br.ufma.lsdi.cddl.pubsub.SubscriberFactory;


public class MainActivity extends AppCompatActivity {



    private TextView logTextView;
    private View sendButton;
    private ConnectionImpl con;
    private ConnectionImpl conExterno;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setViews();

        initConnectExternalBroker();
        subscribeHMSoft();

        sendButton.setOnClickListener(clickListener);
    }

    @Override
    protected void onDestroy() {
        //ajustar
        conExterno.disconnect();
        super.onDestroy();
    }

    private void initConnectExternalBroker() {
        String host = "lsdi.ufma.br";
        conExterno = ConnectionFactory.createConnection();
        conExterno.setClientId("amanda.cardoso@lsdi.ufma.br");
        conExterno.setHost(host);
        conExterno.addConnectionListener(connectionListener);
        conExterno.setPublishConnectionChangedStatus(true);
        conExterno.connect();

    }

    protected void subscribeHMSoft(){
        Subscriber sub = SubscriberFactory.createSubscriber();
        sub.addConnection(conExterno);
        sub.subscribeServiceByName("HMSoft");
        sub.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {

                Log.d("_MAIN", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>" + message);
                processData(message.getServiceValue());


            }
        });
    }

    private void processData(Object [] value) {

        runOnUiThread(() -> {
            TextView dataTextTemperature = (TextView) findViewById(R.id.textViewTemperature);
            TextView dataTextHumidity = (TextView) findViewById(R.id.textViewHumidity);
            TextView dataTextGas = (TextView) findViewById(R.id.textViewGasPresence);

            String temperatura =  Double.toString ((Double) value[0]);
            String umidade = Double.toString ((Double) value[1]);
            String gas = Double.toString ((Double) value[2]);

            dataTextTemperature.setText("Temperatura: " + temperatura + "°C");
            dataTextHumidity.setText("Umidade: " + umidade + "%");
            dataTextGas.setText("Presença de gás: " + gas);

        });
    }

        private void setViews() {
        sendButton = findViewById(R.id.buttonSend);
        logTextView = (TextView) findViewById(R.id.logTextView);

    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {


            Publisher publisher = PublisherFactory.createPublisher();
            publisher.addConnection(conExterno);

            TextView textoTemperatura = (TextView) findViewById(R.id.editTextCommand);

            if(isValidTemperature(textoTemperatura.getText().toString())){
                Toast.makeText(getApplicationContext(), "Temperatura válida", Toast.LENGTH_SHORT).show();


                String comandoOriginal = textoTemperatura.getText().toString() + ";";
                String comandoFormatado = formataComando(comandoOriginal); //transforma para bytes e depois string


                String m = String.format("{\"characteristicUUID\": \"00002a6f-0000-1000-8000-00805f9b34fb\", \"command\": %s}", comandoFormatado);

                CommandMessage cm = new CommandMessage("MHUB_SALA_ETS",
                        new MOUUID(TechnologyID.BLE.id, "D4:36:39:DB:34:68").toString(),
                        "HMSoft",
                        m);
                //cm.setServiceValue(m);
                publisher.publish(cm);

            }else{

                Toast.makeText(getApplicationContext(), "Temperatura inválida", Toast.LENGTH_SHORT).show();

            }

        }
    };


    private IConnectionListener connectionListener = new IConnectionListener() {
        @Override
        public void onConnectionEstablished() {
            logTextView.setText("Conexão estabelecida.");
        }

        @Override
        public void onConnectionEstablishmentFailed() {
            logTextView.setText("Falha na conexão.");
        }

        @Override
        public void onConnectionLost() {
            logTextView.setText("Conexão perdida.");
        }

        @Override
        public void onDisconnectedNormally() {
            logTextView.setText("Uma disconexão normal ocorreu.");
        }

    };





    private boolean checkPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int result = ContextCompat.checkSelfPermission(getApplicationContext(), READ_EXTERNAL_STORAGE);
            int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED;
        }
    }

    private static ISubscriberListener subscriberListener = new ISubscriberListener() {

        @Override
        public void onMessageArrived(Message message) {

            if (message instanceof SensorDataMessage) {
                final SensorDataMessage sensorDataMessage = (SensorDataMessage) message;

                System.out.println("Message received >>>"+sensorDataMessage);

            }
            if (message instanceof QueryResponseMessage) {
                QueryResponseMessage qrm = (QueryResponseMessage) message;

                for (ServiceInformationMessage sim : qrm.getServiceInformationMessageList()) {
                    System.out.println("RESPOSTA DA QUERY " + sim.getServiceName() + " - " + sim.getAccuracy() + " - " + sim.getAge());
                }
                return;
            }
        }

    };

    String formataComando(String comando){

        byte[] commandBytes = comando.getBytes(StandardCharsets.UTF_8); //cria um array de bytes do tipo [50,50,59]
        String commandString = Arrays.toString(commandBytes); //transforma para string

        return commandString;

    }


    private boolean isValidTemperature(String input) {
        if (input.isEmpty()) {
            return false;
        }

        try {
            int number = Integer.parseInt(input);
            return number == 0 || (number >= 16 && number <= 30);
        } catch (NumberFormatException e) {
            return false;
        }
    }


}
