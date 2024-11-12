package com.example.monitoramentoambiente10;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import br.ufma.lsdi.cddl.CDDL;
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

public class SalaEtsActivity extends AppCompatActivity {

    private TextView logTextView;
    private View sendButton;
    private ConnectionImpl con;
    private ConnectionImpl conExterno;
    private CDDL cddl;
    Subscriber sub;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sala_das_ets);
        checkPermission();
        setViews();
        initConnectExternalBroker();
        subscribeHMSoft();
        sendButton.setOnClickListener(clickListener);
    }



    @Override
    protected void onDestroy() {

        conExterno.unsubscribeAll();
        conExterno.disconnect();
        super.onDestroy();
    }

    private void initCDDL() {

        initConnectExternalBroker();
        cddl = CDDL.getInstance();
        cddl.setConnection(conExterno);
        cddl.setContext(this);
        cddl.startService();
        cddl.startCommunicationTechnology(CDDL.INTERNAL_TECHNOLOGY_ID);
        cddl.startCommunicationTechnology(CDDL.BLE_TECHNOLOGY_ID);
        sub = SubscriberFactory.createSubscriber();
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
        sub = SubscriberFactory.createSubscriber();
        sub.addConnection(conExterno);
        sub.subscribeServiceByName("HMSoft");
        sub.setSubscriberListener(new ISubscriberListener() {
            @Override
            public void onMessageArrived(Message message) {
                Log.d("_MAIN", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>" + message);
                if(message.getMouuid().equals("1-D4:36:39:DB:34:68")) {
                    processData(message.getServiceValue());
                }

            }

        });

    }

    private void processData(Object [] value) {
        Log.d("_MAIN", "Thread atual: " + Thread.currentThread().getName());
        runOnUiThread(() -> {
            TextView dataTextTemperature = (TextView) findViewById(R.id.textViewTemperature1);
            TextView dataTextHumidity = (TextView) findViewById(R.id.textViewHumidity1);
            TextView dataTextGas = (TextView) findViewById(R.id.textViewGasPresence1);

            String temperatura =  Double.toString ((Double) value[0]);
            String umidade = Double.toString ((Double) value[1]);
            String gas = Double.toString ((Double) value[2]);

            if(gas.equals("1.0")){
                gas = "presente";
            }else{
                gas = "ausente";
            }


            dataTextTemperature.setText("Temperatura: " + temperatura + "°C");
            dataTextHumidity.setText("Umidade: " + umidade + "%");
            dataTextGas.setText("Gás: " + gas);

        });
    }

    private void clearViews(){
        TextView dataTextTemperature = (TextView) findViewById(R.id.textViewTemperature1);
        TextView dataTextHumidity = (TextView) findViewById(R.id.textViewHumidity1);
        TextView dataTextGas = (TextView) findViewById(R.id.textViewGasPresence1);

        dataTextTemperature.setText("");
        dataTextHumidity.setText("");
        dataTextGas.setText("");
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

            if(isValidTemperatureGREE(textoTemperatura.getText().toString())){
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
            Log.d("ConnectionListener", "onConnectionLost chamado");
            logTextView.setText("Conexão perdida.");
            clearViews();

        }

        @Override
        public void onDisconnectedNormally() {
            logTextView.setText("Uma disconexão normal ocorreu.");
            clearViews();
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


    private boolean isValidTemperatureGREE(String input) {
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
