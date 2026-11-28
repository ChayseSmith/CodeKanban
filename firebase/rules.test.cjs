const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, serverTimestamp } = require('firebase/firestore');
(async () => {
  const test = await initializeTestEnvironment({projectId: 'demo-kanban-sync', firestore: {host: '127.0.0.1', port: 8085, rules: fs.readFileSync('firebase/firestore.rules', 'utf8')}});
  try {
    const alice = test.authenticatedContext('alice').firestore();
    const bob = test.authenticatedContext('bob').firestore();
    const guest = test.unauthenticatedContext().firestore();
    const payload = () => ({schema: 1, json: '{"deleted":true}', changeId: 'change-1', updatedAt: serverTimestamp()});
    await assertSucceeds(setDoc(doc(alice, 'users/alice/records/one'), payload()));
    await assertSucceeds(getDoc(doc(alice, 'users/alice/records/one')));
    await assertSucceeds(getDocs(collection(alice, 'users/alice/records')));
    await assertFails(getDoc(doc(bob, 'users/alice/records/one')));
    await assertFails(getDocs(collection(bob, 'users/alice/records')));
    await assertFails(setDoc(doc(bob, 'users/alice/records/two'), payload()));
    await assertFails(getDoc(doc(guest, 'users/alice/records/one')));
    await assertFails(setDoc(doc(guest, 'users/alice/records/two'), payload()));
    await assertFails(setDoc(doc(alice, 'users/alice/records/bad'), {...payload(), schema: 2}));
    await assertFails(setDoc(doc(alice, 'users/alice/records/bad'), {...payload(), admin: true}));
    await assertFails(setDoc(doc(alice, 'users/alice/records/bad'), {...payload(), updatedAt: null}));
    await assertFails(setDoc(doc(alice, 'users/bob/records/bad'), payload()));
    console.log('Firestore owner isolation, unauthenticated denial and write validation passed.');
  } finally { await test.cleanup(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
