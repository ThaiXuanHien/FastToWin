globalThis.FASTTOWIN_CONFIG = {
    serverUrl: `wss://${globalThis.location.host}/game`,
    // Firebase Web config and VAPID key are public identifiers, not server secrets.
    firebase: {
        apiKey: "AIzaSyA8hAGqhwtR3WqVWmVgidGHTf9Kt4dtkc0",
        authDomain: "fast-to-win.firebaseapp.com",
        projectId: "fast-to-win",
        storageBucket: "fast-to-win.firebasestorage.app",
        messagingSenderId: "971269227863",
        appId: "1:971269227863:web:a8eace979899036f626a12"
    },
    vapidKey: "BLFJyO3O-q2GQrjEON6A0oGXsTmv27xlnKsSZFq0FdOPwQMUfxH0qdSCTMHFKXu5-ugMgcphTnkP54coZ_oVwfU"
};
