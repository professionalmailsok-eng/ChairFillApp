import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, PermissionsAndroid, Switch, TouchableOpacity, Alert, TextInput, NativeModules } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

// NOTE: the *.workers.dev domain does not route at the edge — only chairfill.in does.
const SERVER_URL = 'https://chairfill.in/api/calls/missed';

// Native bridge that persists clinic_id + on/off toggle to SharedPreferences,
// which the background PhoneStateReceiver reads when a call is missed.
const { ConfigModule } = NativeModules;

export default function App() {
  const [hasPermissions, setHasPermissions] = useState(false);
  const [isActive, setIsActive] = useState(false);
  const [clinicId, setClinicId] = useState('');
  const [savedClinicId, setSavedClinicId] = useState('');

  useEffect(() => {
    checkPermissions();
    loadState();
  }, []);

  // Push the current clinic_id + active flag down to native SharedPreferences.
  const syncNative = async (cid: string, active: boolean) => {
    try {
      if (ConfigModule?.saveConfig) {
        await ConfigModule.saveConfig(cid, active);
      }
    } catch (e) {
      console.warn('Failed to sync config to native', e);
    }
  };

  const loadState = async () => {
    const state = await AsyncStorage.getItem('isActive');
    const active = state === 'true';
    if (state !== null) setIsActive(active);

    const cid = (await AsyncStorage.getItem('clinicId')) || '';
    setClinicId(cid);
    setSavedClinicId(cid);

    // Make sure native always has the latest config on launch.
    syncNative(cid, active);
  };

  const saveClinicId = async () => {
    const cid = clinicId.trim();
    if (!cid) {
      Alert.alert('Clinic code required', 'Please enter the clinic code from your ChairFill dashboard.');
      return;
    }
    await AsyncStorage.setItem('clinicId', cid);
    setSavedClinicId(cid);
    await syncNative(cid, isActive);
    Alert.alert('Saved', 'Clinic code saved. Missed calls will now be linked to this clinic.');
  };

  const toggleActive = async (value: boolean) => {
    if (value && !savedClinicId) {
      Alert.alert('Clinic code required', 'Please enter and save your clinic code before turning on monitoring.');
      return;
    }
    if (value && !hasPermissions) {
      const granted = await requestPermissions();
      if (!granted) {
        Alert.alert('Permissions Required', 'Call tracking requires Phone State and Call Log permissions.');
        return;
      }
    }
    setIsActive(value);
    await AsyncStorage.setItem('isActive', value.toString());
    await syncNative(savedClinicId, value);
  };

  // Call this function when your native module detects a missed call
  const sendMissedCallToChairfill = async (missedCallerNumber: string) => {
    try {
      const response = await fetch(SERVER_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          phone_number: missedCallerNumber,
          clinic_id: clinicId,
          timestamp: new Date().toISOString()
        })
      });
      
      const result = await response.json();
      console.log('Chairfill Webhook Response:', result);
    } catch (error) {
      console.error('Error sending missed call to Chairfill:', error);
    }
  };

  const checkPermissions = async () => {
    const phoneState = await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE);
    const callLog = await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.READ_CALL_LOG);
    setHasPermissions(phoneState && callLog);
  };

  const requestPermissions = async () => {
    try {
      const granted = await PermissionsAndroid.requestMultiple([
        PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
        PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
      ]);
      const allGranted = 
        granted[PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE] === PermissionsAndroid.RESULTS.GRANTED &&
        granted[PermissionsAndroid.PERMISSIONS.READ_CALL_LOG] === PermissionsAndroid.RESULTS.GRANTED;
      
      setHasPermissions(allGranted);
      return allGranted;
    } catch (err) {
      console.warn(err);
      return false;
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>ChairFill Background Sync</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.statusTitle}>Clinic Code</Text>
        <Text style={styles.statusSubtitle}>Paste your clinic ID from the ChairFill dashboard</Text>
        <TextInput
          style={styles.input}
          value={clinicId}
          onChangeText={setClinicId}
          placeholder="e.g. 1a2b3c4d-5e6f-..."
          placeholderTextColor="#64748b"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity style={styles.saveButton} onPress={saveClinicId}>
          <Text style={styles.saveButtonText}>Save Clinic Code</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.card}>
        <View style={styles.statusRow}>
          <View>
            <Text style={styles.statusTitle}>Missed Call Monitoring</Text>
            <Text style={styles.statusSubtitle}>{isActive ? 'Running in background' : 'Paused'}</Text>
          </View>
          <Switch
            value={isActive}
            onValueChange={toggleActive}
            trackColor={{ false: '#334155', true: '#4ade80' }}
            thumbColor={isActive ? '#ffffff' : '#94a3b8'}
          />
        </View>
      </View>

      {!hasPermissions && (
        <View style={styles.warningCard}>
          <Text style={styles.warningText}>
            Permissions missing. Please grant phone state and call log access to allow ChairFill to detect missed calls.
          </Text>
          <TouchableOpacity style={styles.permissionButton} onPress={requestPermissions}>
             <Text style={styles.permissionButtonText}>Grant Permissions</Text>
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.infoCard}>
        <Text style={styles.infoTitle}>How it works</Text>
        <Text style={styles.infoText}>
          When a call to this phone is missed, this app instantly securely transmits the caller's number to your ChairFill AI Receptionist. 
        </Text>
      </View>
      <Text style={styles.footerText}>{savedClinicId ? `Connected as: ${savedClinicId}` : 'Not connected — enter your clinic code above'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f172a', padding: 16 },
  header: { marginTop: 40, marginBottom: 24 },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: '#ffffff' },
  card: { backgroundColor: '#1e293b', borderRadius: 16, padding: 20, marginBottom: 16, borderWidth: 1, borderColor: '#334155' },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  statusTitle: { color: '#f8fafc', fontSize: 16, fontWeight: '600' },
  statusSubtitle: { color: '#94a3b8', fontSize: 14, marginTop: 4 },
  input: { backgroundColor: '#0f172a', borderWidth: 1, borderColor: '#334155', borderRadius: 10, color: '#f8fafc', paddingHorizontal: 12, paddingVertical: 10, marginTop: 12, fontSize: 14 },
  saveButton: { backgroundColor: '#4ade80', paddingVertical: 10, borderRadius: 10, alignItems: 'center', marginTop: 12 },
  saveButtonText: { color: '#0f172a', fontWeight: '700', fontSize: 14 },
  warningCard: { backgroundColor: 'rgba(245, 158, 11, 0.1)', borderRadius: 16, padding: 20, marginBottom: 16, borderWidth: 1, borderColor: 'rgba(245, 158, 11, 0.3)' },
  warningText: { color: '#fcd34d', fontSize: 14, marginBottom: 12 },
  permissionButton: { backgroundColor: 'rgba(245, 158, 11, 0.2)', paddingVertical: 8, paddingHorizontal: 16, borderRadius: 8, alignSelf: 'flex-start' },
  permissionButtonText: { color: '#fbbf24', fontWeight: '600' },
  infoCard: { backgroundColor: 'transparent', borderRadius: 16, padding: 20, marginTop: 8 },
  infoTitle: { color: '#cbd5e1', fontSize: 16, fontWeight: 'bold', marginBottom: 8 },
  infoText: { color: '#94a3b8', fontSize: 14, lineHeight: 22 },
  footerText: { color: '#64748b', textAlign: 'center', position: 'absolute', bottom: 30, left: 0, right: 0 }
});