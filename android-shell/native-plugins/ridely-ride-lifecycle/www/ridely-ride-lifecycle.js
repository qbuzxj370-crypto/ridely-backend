var exec = require('cordova/exec');

module.exports = {
    setActive: function (isActive, success, error) {
        exec(success, error, 'RidelyRideLifecycle', 'setActive', [!!isActive]);
    },
    consumePendingForceEnd: function (success, error) {
        exec(success, error, 'RidelyRideLifecycle', 'consumePendingForceEnd', []);
    }
};
