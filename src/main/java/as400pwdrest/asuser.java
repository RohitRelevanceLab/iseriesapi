package as400pwdrest;
import as400pwdrest.aesuser.Auser;
import com.ibm.as400.access.*;

import javax.annotation.Resource;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import as400pwdrest.aesuser.Auser.*;

//password reset params
//$userprofile,$password,$server,$reasonReset
//Version 1.3 updated to differentiate elevated and non profile.

public class asuser {

    private static Properties prop = new Properties();


    public static String removeProfile(String profileName,String system,as400pwdrest.AES256 aes256){
        String returnState = "";
        if(prop.getProperty("server."+system) == null){
            System.out.println("Server resource not found. "+system);
            returnState = "Error";
            return returnState;
        }
        String decPass = aes256.decrypt(prop.getProperty("password."+system),"secret");
        AS400 as400 = new AS400(prop.getProperty("server."+system),prop.getProperty("username."+system),decPass);
        try {

            as400.connectService(AS400.COMMAND);
            System.out.println("Connected:"+as400.isConnected());
            //String cmdTxt = "CALL PGM(QGPL/BOTRESET01) PARM('" + profileName.toUpperCase() + "' 'testpass1')";
            String cmdTxt = "CALL PGM(BOTUSRDEL) PARM('" + profileName.toUpperCase() + "')";
            System.out.println("Command used: " + cmdTxt);
            CommandCall cmd = new CommandCall(as400);

            if(cmd.run(cmdTxt) != true){
                //System.out.println("Not a valid command");
                System.out.println("Issued Command");
            }
            else{
                System.out.println("Issued Command");
            }
            AS400Message[] messages = cmd.getMessageList();
//AS400Message (ID: CRJ0001 text: Profile does not exist.):com.ibm.as400.access.AS400Message@65466a6a
            for (int i = 0; i < messages.length; i++) {

                if (messages[i].getText().contains("Profile does not exist.")) {
                    returnState = "NotFound";
                }
                if (messages[i].getText().contains("Profile has elevated authorities")) {
                    returnState = "Elevated";
                }
                if (messages[i].getText().contains("has been deleted") && (returnState == "")) {
                    returnState = "Removed";
                }
                System.out.println(messages[i].getText());
            }

        }
        catch (Exception e){
            System.out.println(e.getMessage());
            returnState = "Error";
        }
        finally {
            System.out.println("Disconnecting Service");
            as400.disconnectAllServices();
            System.out.println("Connected:"+as400.isConnected());
        }



        return returnState;
    }



    public static void main(String args[]) throws Exception {
//        String systemName = args[0];
//        String userId = args[1];
//        String password = args[2];
//        String userFilter = args[3];

       // appProps.load(new FileInputStream(appConfigPath));

       // prop.setProperty("")
       // System.out.println(System.getProperty("user.dir"));
        prop.load(new FileInputStream(new File(System.getProperty("user.dir")+"\\app.properties")));

        as400pwdrest.AES256 aes256 = new as400pwdrest.AES256();



        if(args.length <= 0){
            System.out.println("Insufficient arguments passed. \n1. Please do provide the <userprofile>  <password> <server> to update");
            System.out.println("Or to remove profile . \n1. Please do provide the \"<userprofile>  <server> remove\" to remove profile");
            System.out.println(" \n2. To generate an encrypted key pass the key text as the first argument");
            return;
        }
        if(args.length == 1){
            //section for encrypting a passphrase
            System.out.println(aes256.encrypt(args[0],"secret"));
            return;

        }




        //section to control account termination
        //arguments would be <profile> <server> "remove"
        String userId = args[0];
        String system = "";
        String password = "";
        if(args.length == 3){
            //should be a remove command
            if(args[2].toLowerCase().equals("remove")){
                system = args[1];
                String removeStatus = removeProfile(userId,system,aes256);
                if(removeStatus == "Removed"){
                    System.out.println("Profile removed successfully.");
                }
                else{
                    System.out.println("Error removing profile. "+removeStatus);
                }
                return;
            }
            else{
                System.out.println("Only profile remove process implemented. \n1. Please do provide  \"<userprofile> <server> remove\" to remove profile");
                return;
            }
        }


        password = args[1];
        system = args[2];
        Boolean  activateOnly = false;
        Boolean createAccount = false;

        if(args.length > 3) {
            if (args[3].equals("yes")) {
                activateOnly = true;
            }
            if (args[3].equals("create")){
                createAccount = true;
            }
        }

        //Checking if the property is available
        if(prop.getProperty("server."+system) == null){
            System.out.println("Server resource not found. "+system);
            return;
        }



        String decPass = aes256.decrypt(prop.getProperty("password."+system),"secret");
        if(createAccount){
            Auser au = new Auser(prop.getProperty("server."+system),prop.getProperty("username."+system),decPass);

            au.setProfileName(userId.toUpperCase());
            au.close();

            return;

        }

        //System.out.println(prop.getProperty("server."+system) + prop.getProperty("username."+system) + decPass.length());

        AS400 as400 = new AS400(prop.getProperty("server."+system),prop.getProperty("username."+system),decPass);

        try {

            System.out.println("Connecting to :"+prop.getProperty("server."+system));
            as400.connectService(AS400.COMMAND);
            System.out.println("Connected:"+as400.isConnected());

            CommandCall cmd = new CommandCall(as400);
            //String cmdTxt = "dspusrprf usrprf("+userId+")";
            String cmdTxt = "";
            if(activateOnly){
                //cmdTxt = "CHGUSRPRF USRPRF("+userId.toUpperCase()+") STATUS(*ENABLED)";
                cmdTxt = "CALL PGM(BOTENABLE) PARM('"+userId.toUpperCase()+"')";
            }
            else {
                //    cmdTxt ="chgusrprf usrprf(CCGV5R2EYO) password(OKTABOT) status(*enabled) pwdexp(*yes)";
                if (prop.getProperty("command." + system) != null) {
                    //cmdTxt = "Call resetprf parm("+userId+")";
                    //Updated command as of 2-oct-2018
                    //cmdTxt = "CALL PGM(BOTRESET01) PARM('" + userId + "' '"+ password+"')";

                    //Verifying user details

                    User usr = new User(as400,"bottst1");
                    String usrdescription = usr.getDetailInSTRAUTCOL();

                    //usr.refresh();
                    //String[] usrdescription = usr.getUserOptions();
                    cmdTxt = "CALL PGM(QGPL/BOTRESET01) PARM('" + userId.toUpperCase() + "' '" + password + "')";
                    System.out.println("Command used: " + cmdTxt);
                    //System.out.println("Custom Command");
                }
            }

            if(cmd.run(cmdTxt) != true){
                //System.out.println("Not a valid command");
                System.out.println("Issued Command");
            }
            else{
                System.out.println("Issued Command");
            }
            AS400Message[] messages = cmd.getMessageList();

            for (int i = 0; i < messages.length; i++) {
                if (messages[i].getText().contains(password)) {
                    System.out.println("Command ran successfully");
                }
                System.out.println(messages[i].getText());
            }



        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
        finally {
            System.out.println("Disconnecting Service");
            as400.disconnectAllServices();
            System.out.println("Connected:"+as400.isConnected());
        }
    }
}
