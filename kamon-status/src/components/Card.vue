<template>
  <div class="outer py-1">
    <slot/>
  </div>
</template>


<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped lang="scss">
.outer {
  background-color: white;
  box-shadow: 0 2px 9px 1px rgba(0, 0, 0, 0.1);
}

hr {
  margin: 1px;
  border-color: #f3f3f3;
}
</style>
