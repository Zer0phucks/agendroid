package com.agendroid.core.common.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.agendroid.core.common.di.DefaultDispatcher")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class DispatcherModule_DefaultDispFactory implements Factory<CoroutineDispatcher> {
  @Override
  public CoroutineDispatcher get() {
    return defaultDisp();
  }

  public static DispatcherModule_DefaultDispFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineDispatcher defaultDisp() {
    return Preconditions.checkNotNullFromProvides(DispatcherModule.INSTANCE.defaultDisp());
  }

  private static final class InstanceHolder {
    private static final DispatcherModule_DefaultDispFactory INSTANCE = new DispatcherModule_DefaultDispFactory();
  }
}
