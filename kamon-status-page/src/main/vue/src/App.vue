<template>
  <div id="app">
    <div class="header w-100 mb-1 sticky-top">
      <div class="container h-100">
        <div class="row h-100 justify-content-between">
          <div class="col-auto h-100">
            <img class="logo h-100 img-fluid" src="./assets/logo.svg" alt="">
          </div>
        </div>
      </div>
    </div>

    <router-view/>
  </div>

</template>

<style lang="scss">

$header-height: 85px;

.header {
  height: $header-height;
  background-color: white;
  box-shadow: 0 5px 9px 1px rgba(0,0,0,0.1);

  .navigation {
    line-height: $header-height;

    .navigation-link, a {
      display: inline-block;
      padding: 0 0.5rem;
      text-transform: uppercase;
      text-decoration: none;
      color: #b3b3b3;

      &:hover {
        color: #888888;
      }
    }
  }

  .logo {
    padding: 1rem 0rem;
  }
}
</style>
