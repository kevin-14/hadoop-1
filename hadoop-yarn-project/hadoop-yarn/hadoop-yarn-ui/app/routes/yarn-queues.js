import Ember from 'ember';

export default Ember.Route.extend({
  model() {
    return this.store.findAll('yarnQueue');
  },

  afterModel() {
  	setTimeout(function() {
  		this.controllerFor("yarnQueues").set("loading", false);
  	}.bind(this), 500);
  }
});
