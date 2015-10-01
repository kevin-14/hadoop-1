export default {
  containerIdToAttemptId: function(containerId) {
    if (containerId) {
      var arr = containerId.split('_');
      var attemptId = ["appattempt", arr[1], 
        arr[2], this.padding(arr[3], 6)];
      return attemptId.join('_');
    }
  },
  padding: function(str, toLen) {
    if (str.length >= toLen) {
      return str;
    }
    return '0'.repeat(toLen - str.length) + str;
  }
}
