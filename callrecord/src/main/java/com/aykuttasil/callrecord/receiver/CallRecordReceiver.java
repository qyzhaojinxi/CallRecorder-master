package com.aykuttasil.callrecord.receiver;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.aykuttasil.callrecord.CallRecord;
import com.aykuttasil.callrecord.helper.PrefsHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by aykutasil on 19.10.2016.
 */
public class CallRecordReceiver extends PhoneCallReceiver {


    private static final String TAG = CallRecordReceiver.class.getSimpleName();
    public static final String CALL_RECORDER_LOG = "/miui_is_close_recorder_log";

    public static final String ACTION_IN = "android.intent.action.PHONE_STATE";
    public static final String ACTION_OUT = "android.intent.action.NEW_OUTGOING_CALL";
    public static final String EXTRA_PHONE_NUMBER = "android.intent.extra.PHONE_NUMBER";

    protected CallRecord callRecord;
    private static MediaRecorder recorder;
    private File audiofile;
    private boolean isRecordStarted = false;


    public CallRecordReceiver(CallRecord callRecord) {
        this.callRecord = callRecord;
    }

    @Override
    protected void onIncomingCallReceived(Context context, String number, Date start) {

    }

    @Override
    protected void onIncomingCallAnswered(final Context context, final String number, Date start) {
        //TODO 延迟发送

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startRecord(context, "incoming", number);
            }
        },5000);
    }

    @Override
    protected void onIncomingCallEnded(Context context, String number, Date start, Date end) {
        stopRecord(context);
    }

    @Override
    protected void onOutgoingCallStarted(final Context context, final String number, Date start) {
        //todo
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startRecord(context, "outgoing", number);
            }
        },5000);

    }

    @Override
    protected void onOutgoingCallEnded(Context context, String number, Date start, Date end) {
        stopRecord(context);
    }

    @Override
    protected void onMissedCall(Context context, String number, Date start) {

    }

    // Derived classes could override these to respond to specific events of interest
    protected void onRecordingStarted(Context context, CallRecord callRecord, File audioFile) {
    }

    protected void onRecordingFinished(Context context, CallRecord callRecord, File audioFile) {
    }

    private void startRecord(Context context, String seed, String phoneNumber) {
        boolean isOpenRecorder = false;
        try {
            boolean isSaveFile = PrefsHelper.readPrefBool(context, CallRecord.PREF_SAVE_FILE);
            Log.i(TAG, "isSaveFile: " + isSaveFile);

            // dosya kayıt edilsin mi?
            if (!isSaveFile) {
                return;
            }

            if (isRecordStarted) {
                try {
                    recorder.stop();  // stop the recording
                } catch (RuntimeException e) {
                    // RuntimeException is thrown when stop() is called immediately after start().
                    // In this case the output file is not properly constructed ans should be deleted.
                    Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                    //noinspection ResultOfMethodCallIgnored
                    audiofile.delete();
                }
                releaseMediaRecorder();
                isRecordStarted = false;
            } else {
                if (prepareAudioRecorder(context, seed, phoneNumber)) {
                    recorder.start();
                    isRecordStarted = true;
                    onRecordingStarted(context, callRecord, audiofile);
                    Log.i(TAG, "record start");
                } else {
                    releaseMediaRecorder();
                }
                //new MediaPrepareTask().execute(null, null, null);
            }
        } catch (IllegalStateException e) {
            //todo 监控是否开启了小米内部录音。有开启则此处录音设备被占用，会报错。没开启，则不会进入这里。
            Log.e(TAG,"------>录音设备被占用");
            isOpenRecorder = true;

            releaseMediaRecorder();
        } catch (RuntimeException e) {
            Log.e(TAG,"------>录音设备被占用");
            e.printStackTrace();
            releaseMediaRecorder();
        } catch (Exception e) {
            Log.e(TAG,"------>录音设备被占用");
            e.printStackTrace();
            releaseMediaRecorder();
        }finally {
            if (isOpenRecorder){

            }else {
                writeTextToFile(getDateNow()+":"+"没有开通话录音",Environment.getExternalStorageDirectory().getPath()+"/"+CALL_RECORDER_LOG,getDateNow());

            }

        }
    }

    private String getDateNow(){
        long currentTime = System.currentTimeMillis();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy年-MM月dd日-HH时mm分ss秒");
        Date date = new Date(currentTime);
//        System.out.println();
        return formatter.format(date);
    }
    private void stopRecord(Context context) {
        try {
            if (recorder != null && isRecordStarted) {
                releaseMediaRecorder();
                isRecordStarted = false;
                onRecordingFinished(context, callRecord, audiofile);
                Log.i(TAG, "record stop");
            }
        } catch (Exception e) {
            releaseMediaRecorder();
            e.printStackTrace();
        }
    }

    private boolean prepareAudioRecorder(Context context, String seed, String phoneNumber) {
        try {
            String file_name = PrefsHelper.readPrefString(context, CallRecord.PREF_FILE_NAME);
            String dir_path = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_PATH);
            String dir_name = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_NAME);
            boolean show_seed = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_SEED);
            boolean show_phone_number = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_PHONE_NUMBER);
            int output_format = PrefsHelper.readPrefInt(context, CallRecord.PREF_OUTPUT_FORMAT);
            int audio_source = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_SOURCE);
            int audio_encoder = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_ENCODER);

            Log.e("dir",dir_path + "/" + dir_name);
            File sampleDir = new File(dir_path + "/" + dir_name);

            if (!sampleDir.exists()) {
                sampleDir.mkdirs();
            }

            StringBuilder fileNameBuilder = new StringBuilder();
            fileNameBuilder.append(file_name);
            fileNameBuilder.append("_");

            if (show_seed) {
                fileNameBuilder.append(seed);
                fileNameBuilder.append("_");
            }

            if (show_phone_number) {
                fileNameBuilder.append(phoneNumber);
                fileNameBuilder.append("_");
            }


            file_name = fileNameBuilder.toString();

            String suffix = "";
            switch (output_format) {
                case MediaRecorder.OutputFormat.AMR_NB: {
                    suffix = ".amr";
                    break;
                }
                case MediaRecorder.OutputFormat.AMR_WB: {
                    suffix = ".amr";
                    break;
                }
                case MediaRecorder.OutputFormat.MPEG_4: {
                    suffix = ".mp4";
                    break;
                }
                case MediaRecorder.OutputFormat.THREE_GPP: {
                    suffix = ".3gp";
                    break;
                }
                default: {
                    suffix = ".amr";
                    break;
                }
            }

            audiofile = File.createTempFile(file_name, suffix, sampleDir);

            recorder = new MediaRecorder();
            recorder.setAudioSource(audio_source);
            recorder.setOutputFormat(output_format);
            recorder.setAudioEncoder(audio_encoder);
            recorder.setOutputFile(audiofile.getAbsolutePath());
            recorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override
                public void onError(MediaRecorder mediaRecorder, int i, int i1) {

                }
            });

            try {
                recorder.prepare();
            } catch (IllegalStateException e) {
                Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
                releaseMediaRecorder();
                return false;
            } catch (IOException e) {
                Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
                releaseMediaRecorder();
                return false;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void releaseMediaRecorder() {
        if (recorder != null) {
            recorder.reset();
            recorder.release();
            recorder = null;
        }
    }

    /*
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            if (prepareAudioRecorder(, "", "")) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                recorder.start();
                Log.i(TAG, "record start");
            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            isRecordStarted = true;
            onRecordingStarted(, callRecord, audiofile);
        }
    }
    */

    private void writeTextToFile(String content,String filePath ,String fileName){
        //生成文件夹在生辰我跟建

        File file = new File(filePath,fileName);
        if (!file.exists()){
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                RandomAccessFile randomAccessFile = new RandomAccessFile(file,"rwd");
                try {
                    randomAccessFile.seek(file.length());
                    randomAccessFile.write(content.getBytes());
                    randomAccessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
