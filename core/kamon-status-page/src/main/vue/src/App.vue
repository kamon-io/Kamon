<template>
  <v-app>
    <v-app-bar
      app
      color="dark"
      flat
      height="300"
      absolute
    >
      <div class="d-flex justify-center pa-5 w-100 h-100 text-center">
        <div class="d-flex flex-column">
          <brand />

          <h1 class="white--text my-5">Status page</h1>
        </div>
      </div>

      <v-spacer></v-spacer>
    </v-app-bar>

    <v-main class="background">
      <router-view />
    </v-main>
  </v-app>
</template>


<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'

import Brand from './components/Brand.vue'

@Component({
  name: 'App',

  components: {
    Brand,
  }
})
export default class App extends Vue {}
</script>
