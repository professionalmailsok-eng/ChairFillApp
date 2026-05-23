import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, PermissionsAndroid, Switch, TouchableOpacity, Alert } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

export default function App() {
  const [hasPermissions, setHasPermissions] = useState(false);
  const [isActive, setIsActive] = useState(false);
  const [clinicId, setClinicId] = useState('clinic_12345'); 

  useEffect(() => {
    checkPermissions();
    loadState();
  }, []);

  const loadState = async () => {
    const state = await AsyncStorage.getItem('isActive');
    if (state !== null) setIsActive(state === 'true');
  };

  const toggleActive = async (value: boolean) => {
    if (value && !hasPermissions) {
      const granted = await requestPermissions();
      if (!granted) {
        Alert.alert('Permissions Required', 'Call tracking requires Phone State and Call Log permissions.');
        return;
      }
    }
    setIsActive(value);
    await AsyncStorage.setItem('isActive', value.toString());
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
      <Text style={styles.footerText}>Connected as: {clinicId}</Text>
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
  warningCard: { backgroundColor: 'rgba(245, 158, 11, 0.1)', borderRadius: 16, padding: 20, marginBottom: 16, borderWidth: 1, borderColor: 'rgba(245, 158, 11, 0.3)' },
  warningText: { color: '#fcd34d', fontSize: 14, marginBottom: 12 },
  permissionButton: { backgroundColor: 'rgba(245, 158, 11, 0.2)', paddingVertical: 8, paddingHorizontal: 16, borderRadius: 8, alignSelf: 'flex-start' },
  permissionButtonText: { color: '#fbbf24', fontWeight: '600' },
  infoCard: { backgroundColor: 'transparent', borderRadius: 16, padding: 20, marginTop: 8 },
  infoTitle: { color: '#cbd5e1', fontSize: 16, fontWeight: 'bold', marginBottom: 8 },
  infoText: { color: '#94a3b8', fontSize: 14, lineHeight: 22 },
  footerText: { color: '#64748b', textAlign: 'center', position: 'absolute', bottom: 30, left: 0, right: 0 }
});