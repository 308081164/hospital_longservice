<template>
  <div class="flex w-full h-screen">
    <LoginLeftView />

    <div class="relative flex-1">
      <AuthTopBar />

      <div class="auth-right-wrap">
        <div class="form">
          <h3 class="title">{{ $t('forgetPassword.title') }}</h3>

          <div class="mt-8 text-center">
            <p class="text-base text-gray-600 mb-2">{{ $t('forgetPassword.contactAdmin') }}</p>
            <p class="text-sm text-gray-400">{{ $t('forgetPassword.adminPath') }}</p>
          </div>

          <div style="margin-top: 40px">
            <ElButton class="w-full custom-height" plain @click="toLogin">
              {{ $t('forgetPassword.backBtnText') }}
            </ElButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  defineOptions({ name: 'ForgetPassword' })

  const router = useRouter()

  const toLogin = () => {
    router.push({ name: 'Login' })
  }
</script>

<style scoped>
  @import '../login/style.css';
</style>
