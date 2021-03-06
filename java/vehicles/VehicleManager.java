package zombie.vehicles;

import gnu.trove.list.array.TShortArrayList;
import gnu.trove.map.hash.TShortShortHashMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.GameTime;
import zombie.GameWindow;
import zombie.SoundManager;
import zombie.Lua.LuaEventManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.network.ByteBufferWriter;
import zombie.core.physics.Bullet;
import zombie.core.physics.Transform;
import zombie.core.physics.WorldSimulation;
import zombie.core.raknet.UdpConnection;
import zombie.core.utils.UpdateLimit;
import zombie.debug.DebugLog;
import zombie.debug.DebugType;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.DrainableComboItem;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.scripting.objects.VehicleScript;

public final class VehicleManager {
   public static VehicleManager instance;
   private final VehicleIDMap IDToVehicle;
   private final ArrayList<BaseVehicle> vehicles;
   private boolean idMapDirty;
   private final Transform tempTransform;
   private final ArrayList<BaseVehicle> send;
   private final TShortArrayList vehiclesWaitUpdates;
   private final TShortShortHashMap towedVehicleMap;
   public static short physicsDelay = 500;
   public final UdpConnection[] connected;
   private final float[] tempFloats;
   private final VehicleManager.PosUpdateVars posUpdateVars;
   private final UpdateLimit vehiclesWaitUpdatesFrequency;
   private BaseVehicle tempVehicle;
   private final ArrayList<BaseVehicle.ModelInfo> oldModels;
   private final ArrayList<BaseVehicle.ModelInfo> curModels;
   private final UpdateLimit sendRequestGetPositionFrequency;
   private final UpdateLimit VehiclePhysicSyncPacketLimit;

   public VehicleManager() {
      this.IDToVehicle = VehicleIDMap.instance;
      this.vehicles = new ArrayList();
      this.idMapDirty = true;
      this.tempTransform = new Transform();
      this.send = new ArrayList();
      this.vehiclesWaitUpdates = new TShortArrayList(128);
      this.towedVehicleMap = new TShortShortHashMap();
      this.connected = new UdpConnection[512];
      this.tempFloats = new float[27];
      this.posUpdateVars = new VehicleManager.PosUpdateVars();
      this.vehiclesWaitUpdatesFrequency = new UpdateLimit(1000L);
      this.oldModels = new ArrayList();
      this.curModels = new ArrayList();
      this.sendRequestGetPositionFrequency = new UpdateLimit(500L);
      this.VehiclePhysicSyncPacketLimit = new UpdateLimit(500L);
   }

   private void noise(String var1) {
      if (Core.bDebug) {
      }

   }

   public void registerVehicle(BaseVehicle var1) {
      this.IDToVehicle.put(var1.VehicleID, var1);
      this.idMapDirty = true;
   }

   public void unregisterVehicle(BaseVehicle var1) {
      this.IDToVehicle.remove(var1.VehicleID);
      this.idMapDirty = true;
   }

   public BaseVehicle getVehicleByID(short var1) {
      return this.IDToVehicle.get(var1);
   }

   public ArrayList<BaseVehicle> getVehicles() {
      if (this.idMapDirty) {
         this.vehicles.clear();
         this.IDToVehicle.toArrayList(this.vehicles);
         this.idMapDirty = false;
      }

      return this.vehicles;
   }

   public void removeFromWorld(BaseVehicle var1) {
      if (var1.VehicleID != -1) {
         short var2 = var1.VehicleID;
         if (var1.trace) {
            this.noise("removeFromWorld vehicle id=" + var1.VehicleID);
         }

         this.unregisterVehicle(var1);
         if (GameServer.bServer) {
            for(int var3 = 0; var3 < GameServer.udpEngine.connections.size(); ++var3) {
               UdpConnection var4 = (UdpConnection)GameServer.udpEngine.connections.get(var3);
               if (var1.connectionState[var4.index] != null) {
                  ByteBufferWriter var5 = var4.startPacket();
                  PacketTypes.PacketType.Vehicles.doPacket(var5);
                  var5.bb.put((byte)8);
                  var5.bb.putShort(var1.VehicleID);
                  PacketTypes.PacketType.Vehicles.send(var4);
               }
            }
         }

         if (GameClient.bClient) {
            var1.serverRemovedFromWorld = false;
            if (var1.interpolation != null) {
               var1.interpolation.poolData();
            }
         }
      }

   }

   public void serverUpdate() {
      ArrayList var1 = IsoWorld.instance.CurrentCell.getVehicles();

      int var2;
      for(var2 = 0; var2 < this.connected.length; ++var2) {
         int var3;
         if (this.connected[var2] != null && !GameServer.udpEngine.connections.contains(this.connected[var2])) {
            this.noise("vehicles: dropped connection " + var2);

            for(var3 = 0; var3 < var1.size(); ++var3) {
               ((BaseVehicle)var1.get(var3)).connectionState[var2] = null;
            }

            this.connected[var2] = null;
         } else {
            for(var3 = 0; var3 < var1.size(); ++var3) {
               if (((BaseVehicle)var1.get(var3)).connectionState[var2] != null) {
                  BaseVehicle.ServerVehicleState var10000 = ((BaseVehicle)var1.get(var3)).connectionState[var2];
                  var10000.flags |= ((BaseVehicle)var1.get(var3)).updateFlags;
               }
            }
         }
      }

      for(var2 = 0; var2 < GameServer.udpEngine.connections.size(); ++var2) {
         UdpConnection var6 = (UdpConnection)GameServer.udpEngine.connections.get(var2);
         this.sendVehicles(var6);
         this.connected[var6.index] = var6;
      }

      for(var2 = 0; var2 < var1.size(); ++var2) {
         BaseVehicle var7 = (BaseVehicle)var1.get(var2);
         if ((var7.updateFlags & 19440) != 0) {
            for(int var4 = 0; var4 < var7.getPartCount(); ++var4) {
               VehiclePart var5 = var7.getPartByIndex(var4);
               var5.updateFlags = 0;
            }
         }

         var7.updateFlags = 0;
      }

   }

   private void sendVehicles(UdpConnection var1) {
      if (var1.isFullyConnected()) {
         this.send.clear();
         ArrayList var2 = IsoWorld.instance.CurrentCell.getVehicles();

         for(int var3 = 0; var3 < var2.size(); ++var3) {
            BaseVehicle var4 = (BaseVehicle)var2.get(var3);
            if (var4.VehicleID == -1) {
               var4.VehicleID = this.IDToVehicle.allocateID();
               this.registerVehicle(var4);
            }

            boolean var5 = var1.vehicles.contains(var4.VehicleID);
            if (var5 && !var1.RelevantTo(var4.x, var4.y, (float)(var1.ReleventRange * 10) * 2.0F)) {
               DebugLog.log("removed out-of-bounds vehicle.id=" + var4.VehicleID + " connection=" + var1.index);
               var1.vehicles.remove(var4.VehicleID);
               var5 = false;
            }

            if (var5 || var1.RelevantTo(var4.x, var4.y)) {
               if (var4.connectionState[var1.index] == null) {
                  var4.connectionState[var1.index] = new BaseVehicle.ServerVehicleState();
               }

               BaseVehicle.ServerVehicleState var6 = var4.connectionState[var1.index];
               if (!var5 || var6.shouldSend(var4)) {
                  this.send.add(var4);
                  var1.vehicles.add(var4.VehicleID);
               }
            }
         }

         if (!this.send.isEmpty()) {
            ByteBufferWriter var18 = var1.startPacket();
            PacketTypes.PacketType var19;
            if (this.VehiclePhysicSyncPacketLimit.Check()) {
               var19 = PacketTypes.PacketType.Vehicles;
            } else {
               var19 = PacketTypes.PacketType.VehiclesUnreliable;
            }

            var19.doPacket(var18);

            try {
               ByteBuffer var20 = var18.bb;
               var20.put((byte)5);
               var20.putShort((short)this.send.size());

               for(int var21 = 0; var21 < this.send.size(); ++var21) {
                  BaseVehicle var7 = (BaseVehicle)this.send.get(var21);
                  BaseVehicle.ServerVehicleState var8 = var7.connectionState[var1.index];
                  var20.putShort(var7.VehicleID);
                  var20.putShort(var8.flags);
                  var20.putFloat(var7.x);
                  var20.putFloat(var7.y);
                  var20.putFloat(var7.jniTransform.origin.y);
                  int var9 = var20.position();
                  var20.putShort((short)0);
                  int var10 = var20.position();
                  boolean var11 = (var8.flags & 1) != 0;
                  int var22;
                  int var25;
                  if (var11) {
                     var8.flags = (short)(var8.flags & -2);
                     var7.netPlayerServerSendAuthorisation(var20);
                     var8.setAuthorization(var7);
                     var22 = var20.position();
                     var20.putShort((short)0);
                     var7.save(var20);
                     var25 = var20.position();
                     var20.position(var22);
                     var20.putShort((short)(var25 - var22));
                     var20.position(var25);
                     int var23 = var20.position();
                     int var15 = var20.position() - var10;
                     var20.position(var9);
                     var20.putShort((short)var15);
                     var20.position(var23);
                     this.writePositionOrientation(var20, var7);
                     var8.x = var7.x;
                     var8.y = var7.y;
                     var8.z = var7.jniTransform.origin.y;
                     var8.orient.set(var7.savedRot);
                  } else {
                     if ((var8.flags & 16384) != 0) {
                        var7.netPlayerServerSendAuthorisation(var20);
                        var8.setAuthorization(var7);
                     }

                     if ((var8.flags & 2) != 0) {
                        this.writePositionOrientation(var20, var7);
                        var8.x = var7.x;
                        var8.y = var7.y;
                        var8.z = var7.jniTransform.origin.y;
                        var8.orient.set(var7.savedRot);
                     }

                     if ((var8.flags & 4) != 0) {
                        var20.put((byte)var7.engineState.ordinal());
                        var20.putInt(var7.engineLoudness);
                        var20.putInt(var7.enginePower);
                        var20.putInt(var7.engineQuality);
                     }

                     if ((var8.flags & 4096) != 0) {
                        var20.put((byte)(var7.isHotwired() ? 1 : 0));
                        var20.put((byte)(var7.isHotwiredBroken() ? 1 : 0));
                        var20.put((byte)(var7.isKeysInIgnition() ? 1 : 0));
                        var20.put((byte)(var7.isKeyIsOnDoor() ? 1 : 0));
                        InventoryItem var12 = var7.getCurrentKey();
                        if (var12 == null) {
                           var20.put((byte)0);
                        } else {
                           var20.put((byte)1);
                           var12.saveWithSize(var20, false);
                        }

                        var20.putFloat(var7.getRust());
                        var20.putFloat(var7.getBloodIntensity("Front"));
                        var20.putFloat(var7.getBloodIntensity("Rear"));
                        var20.putFloat(var7.getBloodIntensity("Left"));
                        var20.putFloat(var7.getBloodIntensity("Right"));
                     }

                     if ((var8.flags & 8) != 0) {
                        var20.put((byte)(var7.getHeadlightsOn() ? 1 : 0));
                        var20.put((byte)(var7.getStoplightsOn() ? 1 : 0));

                        for(var22 = 0; var22 < var7.getLightCount(); ++var22) {
                           var20.put((byte)(var7.getLightByIndex(var22).getLight().getActive() ? 1 : 0));
                        }
                     }

                     if ((var8.flags & 1024) != 0) {
                        var20.put((byte)(var7.soundHornOn ? 1 : 0));
                        var20.put((byte)(var7.soundBackMoveOn ? 1 : 0));
                        var20.put((byte)var7.lightbarLightsMode.get());
                        var20.put((byte)var7.lightbarSirenMode.get());
                     }

                     VehiclePart var13;
                     if ((var8.flags & 2048) != 0) {
                        for(var22 = 0; var22 < var7.getPartCount(); ++var22) {
                           var13 = var7.getPartByIndex(var22);
                           if ((var13.updateFlags & 2048) != 0) {
                              var20.put((byte)var22);
                              var20.putInt(var13.getCondition());
                           }
                        }

                        var20.put((byte)-1);
                     }

                     if ((var8.flags & 16) != 0) {
                        for(var22 = 0; var22 < var7.getPartCount(); ++var22) {
                           var13 = var7.getPartByIndex(var22);
                           if ((var13.updateFlags & 16) != 0) {
                              var20.put((byte)var22);
                              var13.getModData().save(var20);
                           }
                        }

                        var20.put((byte)-1);
                     }

                     InventoryItem var14;
                     if ((var8.flags & 32) != 0) {
                        for(var22 = 0; var22 < var7.getPartCount(); ++var22) {
                           var13 = var7.getPartByIndex(var22);
                           if ((var13.updateFlags & 32) != 0) {
                              var14 = var13.getInventoryItem();
                              if (var14 instanceof DrainableComboItem) {
                                 var20.put((byte)var22);
                                 var20.putFloat(((DrainableComboItem)var14).getUsedDelta());
                              }
                           }
                        }

                        var20.put((byte)-1);
                     }

                     if ((var8.flags & 128) != 0) {
                        for(var22 = 0; var22 < var7.getPartCount(); ++var22) {
                           var13 = var7.getPartByIndex(var22);
                           if ((var13.updateFlags & 128) != 0) {
                              var20.put((byte)var22);
                              var14 = var13.getInventoryItem();
                              if (var14 == null) {
                                 var20.put((byte)0);
                              } else {
                                 var20.put((byte)1);

                                 try {
                                    var13.getInventoryItem().saveWithSize(var20, false);
                                 } catch (Exception var16) {
                                    var16.printStackTrace();
                                 }
                              }
                           }
                        }

                        var20.put((byte)-1);
                     }

                     if ((var8.flags & 512) != 0) {
                        for(var22 = 0; var22 < var7.getPartCount(); ++var22) {
                           var13 = var7.getPartByIndex(var22);
                           if ((var13.updateFlags & 512) != 0) {
                              var20.put((byte)var22);
                              var13.getDoor().save(var20);
                           }
                        }

                        var20.put((byte)-1);
                     }

                     if ((var8.flags & 256) != 0) {
                        for(var22 = 0; var22 < var7.getPartCount(); ++var22) {
                           var13 = var7.getPartByIndex(var22);
                           if ((var13.updateFlags & 256) != 0) {
                              var20.put((byte)var22);
                              var13.getWindow().save(var20);
                           }
                        }

                        var20.put((byte)-1);
                     }

                     if ((var8.flags & 64) != 0) {
                        var20.put((byte)var7.models.size());

                        for(var22 = 0; var22 < var7.models.size(); ++var22) {
                           BaseVehicle.ModelInfo var24 = (BaseVehicle.ModelInfo)var7.models.get(var22);
                           var20.put((byte)var24.part.getIndex());
                           var20.put((byte)var24.part.getScriptPart().models.indexOf(var24.scriptModel));
                        }
                     }

                     if ((var8.flags & 8192) != 0) {
                        var20.putFloat((float)var7.engineSpeed);
                        var20.putFloat(var7.throttle);
                     }

                     var22 = var20.position();
                     var25 = var20.position() - var10;
                     var20.position(var9);
                     var20.putShort((short)var25);
                     var20.position(var22);
                  }
               }

               var19.send(var1);
            } catch (Exception var17) {
               var1.cancelPacket();
               var17.printStackTrace();
            }

         }
      }
   }

   public void serverPacket(ByteBuffer var1, UdpConnection var2) {
      byte var3 = var1.get();
      short var4;
      byte var5;
      BaseVehicle var6;
      int var7;
      UdpConnection var8;
      int var11;
      IsoPlayer var12;
      short var13;
      BaseVehicle var15;
      boolean var17;
      short var19;
      BaseVehicle var20;
      IsoGameCharacter var23;
      int var25;
      IsoPlayer var26;
      UdpConnection var27;
      String var29;
      switch(var3) {
      case 1:
         var4 = var1.getShort();
         var5 = var1.get();
         String var22 = GameWindow.ReadString(var1);
         var20 = this.IDToVehicle.get(var4);
         if (var20 != null) {
            var23 = var20.getCharacter(var5);
            if (var23 != null) {
               var20.setCharacterPosition(var23, var5, var22);
               this.sendPassengerPosition(var20, var5, var22, var2);
            }
         }
         break;
      case 2:
         var4 = var1.getShort();
         var5 = var1.get();
         var19 = var1.getShort();
         var20 = this.IDToVehicle.get(var4);
         if (var20 != null) {
            var23 = var20.getCharacter(var5);
            if (var23 != null) {
               var26 = (IsoPlayer)GameServer.IDToPlayerMap.get(var19);
               var29 = var26 == null ? "unknown player" : var26.getUsername();
               DebugLog.log(var29 + " got in same seat as " + ((IsoPlayer)var23).getUsername());
               return;
            }

            for(var25 = 0; var25 < GameServer.udpEngine.connections.size(); ++var25) {
               var27 = (UdpConnection)GameServer.udpEngine.connections.get(var25);

               for(var11 = 0; var11 < 4; ++var11) {
                  var12 = var27.players[var11];
                  if (var12 != null && var12.OnlineID == var19) {
                     this.noise(var12.getUsername() + " got in vehicle " + var20.VehicleID + " seat " + var5);
                     var20.enter(var5, var12);
                     this.sendREnter(var20, var5, var12);
                     var20.authorizationServerOnSeat();
                     break;
                  }
               }
            }

            var26 = (IsoPlayer)GameServer.IDToPlayerMap.get(var19);
            if (var20.getVehicleTowing() != null && var20.getDriver() == var26) {
               var20.getVehicleTowing().setNetPlayerAuthorization((byte)3);
               var20.getVehicleTowing().netPlayerId = var26.OnlineID;
               var20.getVehicleTowing().netPlayerTimeout = 30;
            } else if (var20.getVehicleTowedBy() != null) {
               if (var20.getVehicleTowedBy().getDriver() != null) {
                  var20.setNetPlayerAuthorization((byte)3);
                  var20.netPlayerId = var20.getVehicleTowedBy().getDriver().getOnlineID();
                  var20.netPlayerTimeout = 30;
               } else {
                  var20.setNetPlayerAuthorization((byte)0);
                  var20.netPlayerId = -1;
               }
            }
         }
         break;
      case 3:
         var4 = var1.getShort();
         var13 = var1.getShort();
         var6 = this.IDToVehicle.get(var4);
         if (var6 != null) {
            for(var7 = 0; var7 < GameServer.udpEngine.connections.size(); ++var7) {
               var8 = (UdpConnection)GameServer.udpEngine.connections.get(var7);

               for(var25 = 0; var25 < 4; ++var25) {
                  IsoPlayer var28 = var8.players[var25];
                  if (var28 != null && var28.OnlineID == var13) {
                     var6.exit(var28);
                     this.sendRExit(var6, var28);
                     if (var6.getVehicleTowedBy() == null) {
                        var6.authorizationServerOnSeat();
                     }
                     break;
                  }
               }
            }
         }
         break;
      case 4:
         var4 = var1.getShort();
         var5 = var1.get();
         var19 = var1.getShort();
         var20 = this.IDToVehicle.get(var4);
         if (var20 != null) {
            var23 = var20.getCharacter(var5);
            if (var23 != null) {
               var26 = (IsoPlayer)GameServer.IDToPlayerMap.get(var19);
               var29 = var26 == null ? "unknown player" : var26.getUsername();
               DebugLog.log(var29 + " switched to same seat as " + ((IsoPlayer)var23).getUsername());
               return;
            }

            for(var25 = 0; var25 < GameServer.udpEngine.connections.size(); ++var25) {
               var27 = (UdpConnection)GameServer.udpEngine.connections.get(var25);

               for(var11 = 0; var11 < 4; ++var11) {
                  var12 = var27.players[var11];
                  if (var12 != null && var12.OnlineID == var19) {
                     var20.switchSeat(var12, var5);
                     this.sendSwitchSeat(var20, var5, var12);
                     if (var20.getDriver() == var12) {
                        var20.authorizationServerOnSeat();
                     }
                     break;
                  }
               }
            }
         }
         break;
      case 5:
      case 6:
      case 7:
      case 8:
      case 10:
      case 13:
      default:
         this.noise("unknown vehicle packet " + var3);
         break;
      case 9:
         var4 = var1.getShort();
         var15 = this.IDToVehicle.get(var4);
         if (var15 != null) {
            var17 = var15.authorizationServerOnOwnerData(var2);
            if (var17) {
               float[] var24 = this.tempFloats;
               long var21 = var1.getLong();
               var15.physics.clientForce = var1.getFloat();

               for(int var10 = 0; var10 < var24.length; ++var10) {
                  var24[var10] = var1.getFloat();
               }

               var15.netLinearVelocity.x = var24[7];
               var15.netLinearVelocity.y = var24[8];
               var15.netLinearVelocity.z = var24[9];
               WorldSimulation.instance.setOwnVehiclePhysics(var4, var24);
            }
         }
         break;
      case 11:
         var4 = var1.getShort();

         for(int var16 = 0; var16 < var4; ++var16) {
            var19 = var1.getShort();
            DebugLog.log(DebugType.Vehicle, "send full update for vehicle #" + var19 + " due to request");
            var20 = this.IDToVehicle.get(var19);
            if (var20 != null) {
               BaseVehicle.ServerVehicleState var10000 = var20.connectionState[var2.index];
               var10000.flags = (short)(var10000.flags | 1);
               this.sendVehicles(var2);
            }
         }

         return;
      case 12:
         var4 = var1.getShort();
         var15 = this.IDToVehicle.get(var4);
         if (var15 != null) {
            var15.updateFlags = (short)(var15.updateFlags | 2);
            this.sendVehicles(var2);
         }
         break;
      case 14:
         var4 = var1.getShort();
         float var14 = var1.getFloat();
         float var18 = var1.getFloat();
         var20 = this.IDToVehicle.get(var4);
         if (var20 != null) {
            var20.engineSpeed = (double)var14;
            var20.throttle = var18;
            var20.updateFlags = (short)(var20.updateFlags | 8192);
         }
         break;
      case 15:
         var4 = var1.getShort();
         var13 = var1.getShort();
         var17 = var1.get() == 1;
         var20 = this.IDToVehicle.get(var4);
         if (var20 != null) {
            var20.authorizationServerCollide(var13, var17);
         }
         break;
      case 16:
         var4 = var1.getShort();
         var5 = var1.get();
         var6 = this.IDToVehicle.get(var4);
         if (var6 != null) {
            for(var7 = 0; var7 < GameServer.udpEngine.connections.size(); ++var7) {
               var8 = (UdpConnection)GameServer.udpEngine.connections.get(var7);
               if (var8 != var2) {
                  ByteBufferWriter var9 = var8.startPacket();
                  PacketTypes.PacketType.Vehicles.doPacket(var9);
                  var9.bb.put((byte)16);
                  var9.bb.putShort(var6.VehicleID);
                  var9.bb.put(var5);
                  PacketTypes.PacketType.Vehicles.send(var8);
               }
            }
         }
      }

   }

   public static void serverSendVehiclesConfig(UdpConnection var0) {
      ByteBufferWriter var1 = var0.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var1);
      var1.bb.put((byte)10);
      var1.bb.putShort((short)500);
      PacketTypes.PacketType.Vehicles.send(var0);
   }

   public void serverSendInitialWorldState(UdpConnection var1) {
      ByteBufferWriter var2 = var1.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var2);
      var2.bb.put((byte)19);
      var2.bb.putShort((short)this.towedVehicleMap.size());
      this.towedVehicleMap.forEachEntry((var1x, var2x) -> {
         var2.putShort(var1x);
         var2.putShort(var2x);
         return true;
      });
      PacketTypes.PacketType.Vehicles.send(var1);
   }

   private void vehiclePosUpdate(BaseVehicle var1, float[] var2) {
      byte var3 = 0;
      Transform var4 = this.posUpdateVars.transform;
      Vector3f var5 = this.posUpdateVars.vector3f;
      Quaternionf var6 = this.posUpdateVars.quatf;
      float[] var7 = this.posUpdateVars.wheelSteer;
      float[] var8 = this.posUpdateVars.wheelRotation;
      float[] var9 = this.posUpdateVars.wheelSkidInfo;
      float[] var10 = this.posUpdateVars.wheelSuspensionLength;
      int var24 = var3 + 1;
      float var11 = var2[var3] - WorldSimulation.instance.offsetX;
      float var12 = var2[var24++] - WorldSimulation.instance.offsetY;
      float var13 = var2[var24++];
      var4.origin.set(var11, var13, var12);
      float var14 = var2[var24++];
      float var15 = var2[var24++];
      float var16 = var2[var24++];
      float var17 = var2[var24++];
      var6.set(var14, var15, var16, var17);
      var6.normalize();
      var4.setRotation(var6);
      float var18 = var2[var24++];
      float var19 = var2[var24++];
      float var20 = var2[var24++];
      var5.set(var18, var19, var20);
      int var21 = (int)var2[var24++];

      for(int var22 = 0; var22 < var21; ++var22) {
         var7[var22] = var2[var24++];
         var8[var22] = var2[var24++];
         var9[var22] = var2[var24++];
         var10[var22] = var2[var24++];
      }

      var1.jniTransform.set(var4);
      var1.jniLinearVelocity.set(var5);
      var1.netLinearVelocity.set(var5);
      var1.jniTransform.basis.getScale(var5);
      if ((double)var5.x < 0.99D || (double)var5.y < 0.99D || (double)var5.z < 0.99D) {
         var1.jniTransform.basis.scale(1.0F / var5.x, 1.0F / var5.y, 1.0F / var5.z);
      }

      var1.jniSpeed = var1.jniLinearVelocity.length() * 3.6F;
      Vector3f var26 = var1.getForwardVector(BaseVehicle.allocVector3f());
      if (var26.dot(var1.jniLinearVelocity) < 0.0F) {
         var1.jniSpeed *= -1.0F;
      }

      BaseVehicle.releaseVector3f(var26);

      for(int var23 = 0; var23 < 4; ++var23) {
         var1.wheelInfo[var23].steering = var7[var23];
         var1.wheelInfo[var23].rotation = var8[var23];
         var1.wheelInfo[var23].skidInfo = var9[var23];
         var1.wheelInfo[var23].suspensionLength = var10[var23];
      }

      var1.polyDirty = true;
   }

   public void clientUpdate() {
      int var2;
      if (this.vehiclesWaitUpdatesFrequency.Check()) {
         if (this.vehiclesWaitUpdates.size() > 0) {
            ByteBufferWriter var1 = GameClient.connection.startPacket();
            PacketTypes.PacketType.Vehicles.doPacket(var1);
            var1.bb.put((byte)11);
            var1.bb.putShort((short)this.vehiclesWaitUpdates.size());

            for(var2 = 0; var2 < this.vehiclesWaitUpdates.size(); ++var2) {
               var1.bb.putShort(this.vehiclesWaitUpdates.get(var2));
            }

            PacketTypes.PacketType.Vehicles.send(GameClient.connection);
         }

         this.vehiclesWaitUpdates.clear();
      }

      ArrayList var10 = this.getVehicles();

      for(var2 = 0; var2 < var10.size(); ++var2) {
         BaseVehicle var3 = (BaseVehicle)var10.get(var2);
         if (!var3.isKeyboardControlled() && var3.getJoypad() == -1) {
            float[] var4 = this.tempFloats;
            if (var3.interpolation.interpolationDataGetPR(var4) && var3.netPlayerAuthorization != 3 && var3.netPlayerAuthorization != 1) {
               Bullet.setOwnVehiclePhysics(var3.VehicleID, var4);
               byte var5 = 0;
               int var11 = var5 + 1;
               float var6 = var4[var5];
               float var7 = var4[var11++];
               float var8 = var4[var11++];
               IsoGridSquare var9 = IsoWorld.instance.CurrentCell.getGridSquare((double)var6, (double)var7, 0.0D);
               this.clientUpdateVehiclePos(var3, var6, var7, var8, var9);
               var3.limitPhysicValid.BlockCheck();
               if (GameClient.bClient) {
                  this.vehiclePosUpdate(var3, var4);
               }
            }
         } else {
            var3.interpolation.setVehicleData(var3);
         }
      }

   }

   private void clientUpdateVehiclePos(BaseVehicle var1, float var2, float var3, float var4, IsoGridSquare var5) {
      var1.setX(var2);
      var1.setY(var3);
      var1.setZ(0.0F);
      var1.square = var5;
      var1.setCurrent(var5);
      if (var5 != null) {
         if (var1.chunk != null && var1.chunk != var5.chunk) {
            var1.chunk.vehicles.remove(var1);
         }

         var1.chunk = var1.square.chunk;
         if (!var1.chunk.vehicles.contains(var1)) {
            var1.chunk.vehicles.add(var1);
            IsoChunk.addFromCheckedVehicles(var1);
         }

         if (!var1.addedToWorld) {
            var1.addToWorld();
         }
      } else {
         var1.removeFromWorld();
         var1.removeFromSquare();
      }

      var1.polyDirty = true;
   }

   private void clientReceiveUpdateFull(ByteBuffer var1, short var2, float var3, float var4, float var5) throws IOException {
      byte var6 = var1.get();
      short var7 = var1.getShort();
      short var8 = var1.getShort();
      IsoGridSquare var9 = IsoWorld.instance.CurrentCell.getGridSquare((double)var3, (double)var4, 0.0D);
      if (this.IDToVehicle.containsKey(var2)) {
         BaseVehicle var10 = this.IDToVehicle.get(var2);
         this.noise("ERROR: got full update for KNOWN vehicle id=" + var2);
         var1.get();
         var1.get();
         this.tempVehicle.parts.clear();
         this.tempVehicle.load(var1, 186);
         if (var10.physics != null && (var10.getDriver() == null || !var10.getDriver().isLocal())) {
            this.tempTransform.setRotation(this.tempVehicle.savedRot);
            this.tempTransform.origin.set(var3 - WorldSimulation.instance.offsetX, var5, var4 - WorldSimulation.instance.offsetY);
            var10.setWorldTransform(this.tempTransform);
         }

         var10.netPlayerFromServerUpdate(var6, var7);
         this.clientUpdateVehiclePos(var10, var3, var4, var5, var9);
      } else {
         boolean var15 = var1.get() != 0;
         byte var11 = var1.get();
         if (!var15 || var11 != IsoObject.getFactoryVehicle().getClassID()) {
            DebugLog.log("Error: clientReceiveUpdateFull: packet broken");
         }

         BaseVehicle var12 = new BaseVehicle(IsoWorld.instance.CurrentCell);
         var12.VehicleID = var2;
         var12.square = var9;
         var12.setCurrent(var9);
         var12.load(var1, 186);
         if (var9 != null) {
            var12.chunk = var12.square.chunk;
            var12.chunk.vehicles.add(var12);
            var12.addToWorld();
         }

         IsoChunk.addFromCheckedVehicles(var12);
         var12.netPlayerFromServerUpdate(var6, var7);
         this.registerVehicle(var12);

         for(int var13 = 0; var13 < IsoPlayer.numPlayers; ++var13) {
            IsoPlayer var14 = IsoPlayer.players[var13];
            if (var14 != null && !var14.isDead() && var14.getVehicle() == null) {
               IsoWorld.instance.CurrentCell.putInVehicle(var14);
            }
         }

         if (var12.trace) {
            this.noise("added vehicle id=" + var12.VehicleID + (var9 == null ? " (delayed)" : ""));
         }
      }

   }

   private void clientReceiveUpdate(ByteBuffer var1) throws IOException {
      short var2 = var1.getShort();
      short var3 = var1.getShort();
      float var4 = var1.getFloat();
      float var5 = var1.getFloat();
      float var6 = var1.getFloat();
      short var7 = var1.getShort();
      VehicleCache.vehicleUpdate(var2, var4, var5, 0.0F);
      IsoGridSquare var8 = IsoWorld.instance.CurrentCell.getGridSquare((double)var4, (double)var5, 0.0D);
      BaseVehicle var9 = this.IDToVehicle.get(var2);
      if (var9 == null && var8 == null) {
         if (var1.limit() > var1.position() + var7) {
            var1.position(var1.position() + var7);
         }

      } else {
         boolean var19;
         int var29;
         if (var9 != null && var8 == null) {
            var19 = true;

            for(var29 = 0; var29 < IsoPlayer.numPlayers; ++var29) {
               IsoPlayer var32 = IsoPlayer.players[var29];
               if (var32 != null && var32.getVehicle() == var9) {
                  var19 = false;
                  var32.setPosition(var4, var5, 0.0F);
                  this.sendRequestGetPosition(var2);
               }
            }

            if (var19) {
               var9.removeFromWorld();
               var9.removeFromSquare();
            }

            if (var1.limit() > var1.position() + var7) {
               var1.position(var1.position() + var7);
            }

         } else {
            int var20;
            int var28;
            if ((var3 & 1) != 0) {
               DebugLog.Vehicle.debugln("Receive full update for vehicle #" + var2);
               this.clientReceiveUpdateFull(var1, var2, var4, var5, var6);
               if (var9 == null) {
                  var9 = this.IDToVehicle.get(var2);
               }

               if (!var9.isKeyboardControlled() && var9.getJoypad() == -1) {
                  var1.getLong();
                  byte var35 = 0;
                  float[] var34 = this.tempFloats;
                  var20 = var35 + 1;
                  var34[var35] = var4;
                  var34[var20++] = var5;

                  for(var34[var20++] = var6; var20 < 10; var34[var20++] = var1.getFloat()) {
                  }

                  short var31 = var1.getShort();
                  var34[var20++] = (float)var31;

                  for(var28 = 0; var28 < var31; ++var28) {
                     var34[var20++] = var1.getFloat();
                     var34[var20++] = var1.getFloat();
                     var34[var20++] = var1.getFloat();
                     var34[var20++] = var1.getFloat();
                  }

                  Bullet.setOwnVehiclePhysics(var2, var34);
               } else if (var1.limit() > var1.position() + 102) {
                  var1.position(var1.position() + 102);
               }

               var20 = this.vehiclesWaitUpdates.indexOf(var2);
               if (var20 >= 0) {
                  this.vehiclesWaitUpdates.removeAt(var20);
               }

            } else if (var9 == null && var8 != null) {
               this.sendRequestGetFull(var2);
               if (var1.limit() > var1.position() + var7) {
                  var1.position(var1.position() + var7);
               }

            } else {
               byte var10;
               if ((var3 & 16384) != 0) {
                  var10 = var1.get();
                  short var11 = var1.getShort();
                  if (var9 != null) {
                     var9.netPlayerFromServerUpdate(var10, var11);
                  }
               }

               if ((var3 & 2) != 0) {
                  if (!var9.isKeyboardControlled() && var9.getJoypad() == -1) {
                     var9.interpolation.interpolationDataAdd(var1, var4, var5, var6);
                  } else if (var1.limit() > var1.position() + 102) {
                     var1.position(var1.position() + 102);
                  }
               }

               if ((var3 & 4) != 0) {
                  this.noise("received update Engine id=" + var2);
                  var10 = var1.get();
                  if (var10 >= 0 && var10 < BaseVehicle.engineStateTypes.Values.length) {
                     switch(BaseVehicle.engineStateTypes.Values[var10]) {
                     case Idle:
                        var9.engineDoIdle();
                     case Starting:
                     default:
                        break;
                     case RetryingStarting:
                        var9.engineDoRetryingStarting();
                        break;
                     case StartingSuccess:
                        var9.engineDoStartingSuccess();
                        break;
                     case StartingFailed:
                        var9.engineDoStartingFailed();
                        break;
                     case StartingFailedNoPower:
                        var9.engineDoStartingFailedNoPower();
                        break;
                     case Running:
                        var9.engineDoRunning();
                        break;
                     case Stalling:
                        var9.engineDoStalling();
                        break;
                     case ShutingDown:
                        var9.engineDoShuttingDown();
                     }

                     var9.engineLoudness = var1.getInt();
                     var9.enginePower = var1.getInt();
                     var9.engineQuality = var1.getInt();
                  } else {
                     DebugLog.log("ERROR: VehicleManager.clientReceiveUpdate get invalid data");
                  }
               }

               boolean var21;
               if ((var3 & 4096) != 0) {
                  this.noise("received car properties update id=" + var2);
                  var9.setHotwired(var1.get() == 1);
                  var9.setHotwiredBroken(var1.get() == 1);
                  var19 = var1.get() == 1;
                  var21 = var1.get() == 1;
                  InventoryItem var12 = null;
                  if (var1.get() == 1) {
                     try {
                        var12 = InventoryItem.loadItem(var1, 186);
                     } catch (Exception var18) {
                        var18.printStackTrace();
                     }
                  }

                  var9.syncKeyInIgnition(var19, var21, var12);
                  var9.setRust(var1.getFloat());
                  var9.setBloodIntensity("Front", var1.getFloat());
                  var9.setBloodIntensity("Rear", var1.getFloat());
                  var9.setBloodIntensity("Left", var1.getFloat());
                  var9.setBloodIntensity("Right", var1.getFloat());
               }

               if ((var3 & 8) != 0) {
                  this.noise("received update Lights id=" + var2);
                  var9.setHeadlightsOn(var1.get() == 1);
                  var9.setStoplightsOn(var1.get() == 1);

                  for(var20 = 0; var20 < var9.getLightCount(); ++var20) {
                     var21 = var1.get() == 1;
                     var9.getLightByIndex(var20).getLight().setActive(var21);
                  }
               }

               byte var13;
               byte var23;
               if ((var3 & 1024) != 0) {
                  this.noise("received update Sounds id=" + var2);
                  var19 = var1.get() == 1;
                  var21 = var1.get() == 1;
                  var23 = var1.get();
                  var13 = var1.get();
                  if (var19 != var9.soundHornOn) {
                     if (var19) {
                        var9.onHornStart();
                     } else {
                        var9.onHornStop();
                     }
                  }

                  if (var21 != var9.soundBackMoveOn) {
                     if (var21) {
                        var9.onBackMoveSignalStart();
                     } else {
                        var9.onBackMoveSignalStop();
                     }
                  }

                  if (var9.lightbarLightsMode.get() != var23) {
                     var9.setLightbarLightsMode(var23);
                  }

                  if (var9.lightbarSirenMode.get() != var13) {
                     var9.setLightbarSirenMode(var13);
                  }
               }

               VehiclePart var22;
               if ((var3 & 2048) != 0) {
                  for(var10 = var1.get(); var10 != -1; var10 = var1.get()) {
                     var22 = var9.getPartByIndex(var10);
                     this.noise("received update PartCondition id=" + var2 + " part=" + var22.getId());
                     var22.updateFlags = (short)(var22.updateFlags | 2048);
                     var22.setCondition(var1.getInt());
                  }

                  var9.doDamageOverlay();
               }

               if ((var3 & 16) != 0) {
                  for(var10 = var1.get(); var10 != -1; var10 = var1.get()) {
                     var22 = var9.getPartByIndex(var10);
                     this.noise("received update PartModData id=" + var2 + " part=" + var22.getId());
                     var22.getModData().load(var1, 186);
                     if (var22.isContainer()) {
                        var22.setContainerContentAmount(var22.getContainerContentAmount());
                     }
                  }
               }

               float var24;
               VehiclePart var25;
               InventoryItem var26;
               if ((var3 & 32) != 0) {
                  for(var10 = var1.get(); var10 != -1; var10 = var1.get()) {
                     var24 = var1.getFloat();
                     var25 = var9.getPartByIndex(var10);
                     this.noise("received update PartUsedDelta id=" + var2 + " part=" + var25.getId());
                     var26 = var25.getInventoryItem();
                     if (var26 instanceof DrainableComboItem) {
                        ((DrainableComboItem)var26).setUsedDelta(var24);
                     }
                  }
               }

               if ((var3 & 128) != 0) {
                  for(var10 = var1.get(); var10 != -1; var10 = var1.get()) {
                     var22 = var9.getPartByIndex(var10);
                     this.noise("received update PartItem id=" + var2 + " part=" + var22.getId());
                     var22.updateFlags = (short)(var22.updateFlags | 128);
                     boolean var27 = var1.get() != 0;
                     if (var27) {
                        try {
                           var26 = InventoryItem.loadItem(var1, 186);
                        } catch (Exception var17) {
                           var17.printStackTrace();
                           return;
                        }

                        if (var26 != null) {
                           var22.setInventoryItem(var26);
                        }
                     } else {
                        var22.setInventoryItem((InventoryItem)null);
                     }

                     var28 = var22.getWheelIndex();
                     if (var28 != -1) {
                        var9.setTireRemoved(var28, !var27);
                     }

                     if (var22.isContainer()) {
                        LuaEventManager.triggerEvent("OnContainerUpdate");
                     }
                  }
               }

               if ((var3 & 512) != 0) {
                  for(var10 = var1.get(); var10 != -1; var10 = var1.get()) {
                     var22 = var9.getPartByIndex(var10);
                     this.noise("received update PartDoor id=" + var2 + " part=" + var22.getId());
                     var22.getDoor().load(var1, 186);
                  }

                  LuaEventManager.triggerEvent("OnContainerUpdate");
                  var9.doDamageOverlay();
               }

               if ((var3 & 256) != 0) {
                  for(var10 = var1.get(); var10 != -1; var10 = var1.get()) {
                     var22 = var9.getPartByIndex(var10);
                     this.noise("received update PartWindow id=" + var2 + " part=" + var22.getId());
                     var22.getWindow().load(var1, 186);
                  }

                  var9.doDamageOverlay();
               }

               if ((var3 & 64) != 0) {
                  this.oldModels.clear();
                  this.oldModels.addAll(var9.models);
                  this.curModels.clear();
                  var10 = var1.get();

                  for(var29 = 0; var29 < var10; ++var29) {
                     var23 = var1.get();
                     var13 = var1.get();
                     VehiclePart var14 = var9.getPartByIndex(var23);
                     VehicleScript.Model var15 = (VehicleScript.Model)var14.getScriptPart().models.get(var13);
                     BaseVehicle.ModelInfo var16 = var9.setModelVisible(var14, var15, true);
                     this.curModels.add(var16);
                  }

                  for(var29 = 0; var29 < this.oldModels.size(); ++var29) {
                     BaseVehicle.ModelInfo var30 = (BaseVehicle.ModelInfo)this.oldModels.get(var29);
                     if (!this.curModels.contains(var30)) {
                        var9.setModelVisible(var30.part, var30.scriptModel, false);
                     }
                  }

                  var9.doDamageOverlay();
               }

               if ((var3 & 8192) != 0) {
                  float var33 = var1.getFloat();
                  var24 = var1.getFloat();
                  if (!(var9.getDriver() instanceof IsoPlayer) || !((IsoPlayer)var9.getDriver()).isLocalPlayer()) {
                     var9.engineSpeed = (double)var33;
                     var9.throttle = var24;
                  }
               }

               var19 = false;

               for(var29 = 0; var29 < var9.getPartCount(); ++var29) {
                  var25 = var9.getPartByIndex(var29);
                  if (var25.updateFlags != 0) {
                     if ((var25.updateFlags & 2048) != 0 && (var25.updateFlags & 128) == 0) {
                        var25.doInventoryItemStats(var25.getInventoryItem(), var25.getMechanicSkillInstaller());
                        var19 = true;
                     }

                     var25.updateFlags = 0;
                  }
               }

               if (var19) {
                  var9.updatePartStats();
                  var9.updateBulletStats();
               }

            }
         }
      }
   }

   public void clientPacket(ByteBuffer var1) {
      byte var2 = var1.get();
      short var3;
      int var4;
      short var5;
      BaseVehicle var7;
      BaseVehicle var8;
      short var15;
      byte var16;
      String var10000;
      String var18;
      BaseVehicle var20;
      BaseVehicle var24;
      IsoPlayer var27;
      IsoGameCharacter var29;
      switch(var2) {
      case 1:
         var3 = var1.getShort();
         var16 = var1.get();
         var18 = GameWindow.ReadString(var1);
         var24 = this.IDToVehicle.get(var3);
         if (var24 != null) {
            IsoGameCharacter var28 = var24.getCharacter(var16);
            if (var28 != null) {
               var24.setCharacterPosition(var28, var16, var18);
            }
         }
         break;
      case 2:
      case 3:
      case 9:
      case 10:
      case 11:
      case 12:
      case 14:
      case 15:
      default:
         this.noise("unknown vehicle packet " + var2);
         break;
      case 4:
         var3 = var1.getShort();
         var16 = var1.get();
         var5 = var1.getShort();
         var24 = this.IDToVehicle.get(var3);
         if (var24 != null) {
            var27 = (IsoPlayer)GameClient.IDToPlayerMap.get(var5);
            if (var27 != null) {
               var29 = var24.getCharacter(var16);
               if (var29 == null) {
                  var24.switchSeatRSync(var27, var16);
               } else if (var27 != var29) {
                  var10000 = var27.getUsername();
                  DebugLog.log(var10000 + " switched to same seat as " + ((IsoPlayer)var29).getUsername());
               }
            }
         }
         break;
      case 5:
         if (this.tempVehicle == null || this.tempVehicle.getCell() != IsoWorld.instance.CurrentCell) {
            this.tempVehicle = new BaseVehicle(IsoWorld.instance.CurrentCell);
         }

         var3 = var1.getShort();

         for(var4 = 0; var4 < var3; ++var4) {
            try {
               this.clientReceiveUpdate(var1);
            } catch (Exception var13) {
               var13.printStackTrace();
               return;
            }
         }

         return;
      case 6:
         var3 = var1.getShort();
         var16 = var1.get();
         var5 = var1.getShort();
         var24 = this.IDToVehicle.get(var3);
         if (var24 != null) {
            var27 = (IsoPlayer)GameClient.IDToPlayerMap.get(var5);
            if (var27 != null) {
               var29 = var24.getCharacter(var16);
               if (var29 == null) {
                  DebugLog.log(var27.getUsername() + " got in vehicle " + var24.VehicleID + " seat " + var16);
                  var24.enterRSync(var16, var27, var24);
               } else if (var27 != var29) {
                  var10000 = var27.getUsername();
                  DebugLog.log(var10000 + " got in same seat as " + ((IsoPlayer)var29).getUsername());
               }
            }
         }
         break;
      case 7:
         var3 = var1.getShort();
         var15 = var1.getShort();
         var20 = this.IDToVehicle.get(var3);
         if (var20 != null) {
            IsoPlayer var25 = (IsoPlayer)GameClient.IDToPlayerMap.get(var15);
            if (var25 != null) {
               var20.exitRSync(var25);
            }
         }
         break;
      case 8:
         var3 = var1.getShort();
         if (this.IDToVehicle.containsKey(var3)) {
            BaseVehicle var22 = this.IDToVehicle.get(var3);
            if (var22.trace) {
               this.noise("server removed vehicle id=" + var3);
            }

            var22.serverRemovedFromWorld = true;

            try {
               var22.removeFromWorld();
               var22.removeFromSquare();
            } finally {
               if (this.IDToVehicle.containsKey(var3)) {
                  this.unregisterVehicle(var22);
               }

            }
         }

         VehicleCache.remove(var3);
         break;
      case 13:
         var3 = var1.getShort();
         Vector3f var17 = new Vector3f();
         Vector3f var23 = new Vector3f();
         var17.x = var1.getFloat();
         var17.y = var1.getFloat();
         var17.z = var1.getFloat();
         var23.x = var1.getFloat();
         var23.y = var1.getFloat();
         var23.z = var1.getFloat();
         var24 = this.IDToVehicle.get(var3);
         if (var24 != null) {
            Bullet.applyCentralForceToVehicle(var24.VehicleID, var17.x, var17.y, var17.z);
            Vector3f var26 = var23.cross(var17);
            Bullet.applyTorqueToVehicle(var24.VehicleID, var26.x, var26.y, var26.z);
         }
         break;
      case 16:
         var3 = var1.getShort();
         var16 = var1.get();
         var20 = this.IDToVehicle.get(var3);
         if (var20 != null) {
            SoundManager.instance.PlayWorldSound("VehicleCrash", var20.square, 1.0F, 20.0F, 1.0F, true);
         }
         break;
      case 17:
         var3 = var1.getShort();
         var15 = var1.getShort();
         var18 = GameWindow.ReadString(var1);
         String var21 = GameWindow.ReadString(var1);
         this.towedVehicleMap.put(var3, var15);
         var7 = this.IDToVehicle.get(var3);
         var8 = this.IDToVehicle.get(var15);
         if (var7 != null && var8 != null) {
            var7.addPointConstraint(var8, var18, var21, (Float)null, true);
         }
         break;
      case 18:
         boolean var14 = var1.get() == 1;
         var15 = -1;
         var5 = -1;
         if (var14) {
            var15 = var1.getShort();
         }

         boolean var19 = var1.get() == 1;
         if (var19) {
            var5 = var1.getShort();
         }

         if (this.towedVehicleMap.containsKey(var15)) {
            this.towedVehicleMap.remove(var15);
         }

         if (this.towedVehicleMap.containsKey(var5)) {
            this.towedVehicleMap.remove(var5);
         }

         var7 = this.IDToVehicle.get(var15);
         var8 = this.IDToVehicle.get(var5);
         if (var7 != null || var8 != null) {
            if (var7 != null) {
               var7.breakConstraint(true, true);
            }

            if (var8 != null) {
               var8.breakConstraint(true, true);
            }
         }
         break;
      case 19:
         var3 = var1.getShort();

         for(var4 = 0; var4 < var3; ++var4) {
            var5 = var1.getShort();
            short var6 = var1.getShort();
            this.towedVehicleMap.put(var5, var6);
         }
      }

   }

   public static void loadingClientPacket(ByteBuffer var0) {
      int var1 = var0.position();
      byte var2 = var0.get();
      switch(var2) {
      case 10:
         physicsDelay = var0.getShort();
      default:
         var0.position(var1);
      }
   }

   public void sendCollide(BaseVehicle var1, IsoGameCharacter var2, boolean var3) {
      if (var2 != null) {
         ByteBufferWriter var4 = GameClient.connection.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var4);
         var4.bb.put((byte)15);
         var4.bb.putShort(var1.VehicleID);
         var4.bb.putShort(((IsoPlayer)var2).OnlineID);
         var4.bb.put((byte)(var3 ? 1 : 0));
         PacketTypes.PacketType.Vehicles.send(GameClient.connection);
      }
   }

   public void sendEnter(BaseVehicle var1, int var2, IsoGameCharacter var3) {
      ByteBufferWriter var4 = GameClient.connection.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var4);
      var4.bb.put((byte)2);
      var4.bb.putShort(var1.VehicleID);
      var4.bb.put((byte)var2);
      var4.bb.putShort(((IsoPlayer)var3).OnlineID);
      PacketTypes.PacketType.Vehicles.send(GameClient.connection);
   }

   public static void sendSound(BaseVehicle var0, byte var1) {
      ByteBufferWriter var2 = GameClient.connection.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var2);
      var2.bb.put((byte)16);
      var2.bb.putShort(var0.VehicleID);
      var2.bb.put(var1);
      PacketTypes.PacketType.Vehicles.send(GameClient.connection);
   }

   public static void sendSoundFromServer(BaseVehicle var0, byte var1) {
      for(int var2 = 0; var2 < GameServer.udpEngine.connections.size(); ++var2) {
         UdpConnection var3 = (UdpConnection)GameServer.udpEngine.connections.get(var2);
         ByteBufferWriter var4 = var3.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var4);
         var4.bb.put((byte)16);
         var4.bb.putShort(var0.VehicleID);
         var4.bb.put(var1);
         PacketTypes.PacketType.Vehicles.send(var3);
      }

   }

   public void sendPassengerPosition(BaseVehicle var1, int var2, String var3) {
      ByteBufferWriter var4 = GameClient.connection.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var4);
      var4.bb.put((byte)1);
      var4.bb.putShort(var1.VehicleID);
      var4.bb.put((byte)var2);
      var4.putUTF(var3);
      PacketTypes.PacketType.Vehicles.send(GameClient.connection);
   }

   public void sendPassengerPosition(BaseVehicle var1, int var2, String var3, UdpConnection var4) {
      for(int var5 = 0; var5 < GameServer.udpEngine.connections.size(); ++var5) {
         UdpConnection var6 = (UdpConnection)GameServer.udpEngine.connections.get(var5);
         if (var6 != var4) {
            ByteBufferWriter var7 = var6.startPacket();
            PacketTypes.PacketType.Vehicles.doPacket(var7);
            var7.bb.put((byte)1);
            var7.bb.putShort(var1.VehicleID);
            var7.bb.put((byte)var2);
            var7.putUTF(var3);
            PacketTypes.PacketType.Vehicles.send(var6);
         }
      }

   }

   public void sendRequestGetFull(short var1) {
      if (!this.vehiclesWaitUpdates.contains(var1)) {
         ByteBufferWriter var2 = GameClient.connection.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var2);
         var2.bb.put((byte)11);
         var2.bb.putShort((short)1);
         var2.bb.putShort(var1);
         PacketTypes.PacketType.Vehicles.send(GameClient.connection);
         this.vehiclesWaitUpdates.add(var1);
         DebugLog.log(DebugType.Vehicle, "Send get full request for vehicle #" + var1);
      }
   }

   public void sendRequestGetFull(List<VehicleCache> var1) {
      if (var1 != null) {
         ByteBufferWriter var2 = GameClient.connection.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var2);
         var2.bb.put((byte)11);
         var2.bb.putShort((short)var1.size());

         for(int var3 = 0; var3 < var1.size(); ++var3) {
            var2.bb.putShort(((VehicleCache)var1.get(var3)).id);
            this.vehiclesWaitUpdates.add(((VehicleCache)var1.get(var3)).id);
         }

         PacketTypes.PacketType.Vehicles.send(GameClient.connection);
      }
   }

   public void sendRequestGetPosition(short var1) {
      if (this.sendRequestGetPositionFrequency.Check()) {
         ByteBufferWriter var2 = GameClient.connection.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var2);
         var2.bb.put((byte)12);
         var2.bb.putShort(var1);
         PacketTypes.PacketType.Vehicles.send(GameClient.connection);
         this.vehiclesWaitUpdates.add(var1);
      }
   }

   public void sendAddImpulse(BaseVehicle var1, Vector3f var2, Vector3f var3) {
      UdpConnection var4 = null;

      for(int var5 = 0; var5 < GameServer.udpEngine.connections.size() && var4 == null; ++var5) {
         UdpConnection var6 = (UdpConnection)GameServer.udpEngine.connections.get(var5);

         for(int var7 = 0; var7 < var6.players.length; ++var7) {
            IsoPlayer var8 = var6.players[var7];
            if (var8 != null && var8.getVehicle() != null && var8.getVehicle().VehicleID == var1.VehicleID) {
               var4 = var6;
               break;
            }
         }
      }

      if (var4 != null) {
         ByteBufferWriter var9 = var4.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var9);
         var9.bb.put((byte)13);
         var9.bb.putShort(var1.VehicleID);
         var9.bb.putFloat(var2.x);
         var9.bb.putFloat(var2.y);
         var9.bb.putFloat(var2.z);
         var9.bb.putFloat(var3.x);
         var9.bb.putFloat(var3.y);
         var9.bb.putFloat(var3.z);
         PacketTypes.PacketType.Vehicles.send(var4);
      }

   }

   public void sendREnter(BaseVehicle var1, int var2, IsoGameCharacter var3) {
      for(int var4 = 0; var4 < GameServer.udpEngine.connections.size(); ++var4) {
         UdpConnection var5 = (UdpConnection)GameServer.udpEngine.connections.get(var4);
         ByteBufferWriter var6 = var5.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var6);
         var6.bb.put((byte)6);
         var6.bb.putShort(var1.VehicleID);
         var6.bb.put((byte)var2);
         var6.bb.putShort(((IsoPlayer)var3).OnlineID);
         PacketTypes.PacketType.Vehicles.send(var5);
      }

   }

   public void sendSwitchSeat(BaseVehicle var1, int var2, IsoGameCharacter var3) {
      if (GameClient.bClient) {
         ByteBufferWriter var4 = GameClient.connection.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var4);
         var4.bb.put((byte)4);
         var4.bb.putShort(var1.VehicleID);
         var4.bb.put((byte)var2);
         var4.bb.putShort(((IsoPlayer)var3).OnlineID);
         PacketTypes.PacketType.Vehicles.send(GameClient.connection);
      }

      if (GameServer.bServer) {
         for(int var7 = 0; var7 < GameServer.udpEngine.connections.size(); ++var7) {
            UdpConnection var5 = (UdpConnection)GameServer.udpEngine.connections.get(var7);
            ByteBufferWriter var6 = var5.startPacket();
            PacketTypes.PacketType.Vehicles.doPacket(var6);
            var6.bb.put((byte)4);
            var6.bb.putShort(var1.VehicleID);
            var6.bb.put((byte)var2);
            var6.bb.putShort(((IsoPlayer)var3).OnlineID);
            PacketTypes.PacketType.Vehicles.send(var5);
         }
      }

   }

   public void sendExit(BaseVehicle var1, IsoGameCharacter var2) {
      ByteBufferWriter var3 = GameClient.connection.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var3);
      var3.bb.put((byte)3);
      var3.bb.putShort(var1.VehicleID);
      var3.bb.putShort(((IsoPlayer)var2).OnlineID);
      PacketTypes.PacketType.Vehicles.send(GameClient.connection);
   }

   public void sendRExit(BaseVehicle var1, IsoGameCharacter var2) {
      for(int var3 = 0; var3 < GameServer.udpEngine.connections.size(); ++var3) {
         UdpConnection var4 = (UdpConnection)GameServer.udpEngine.connections.get(var3);
         ByteBufferWriter var5 = var4.startPacket();
         PacketTypes.PacketType.Vehicles.doPacket(var5);
         var5.bb.put((byte)7);
         var5.bb.putShort(var1.VehicleID);
         var5.bb.putShort(((IsoPlayer)var2).OnlineID);
         PacketTypes.PacketType.Vehicles.send(var4);
      }

   }

   public void sendPhysic(BaseVehicle var1) {
      ByteBufferWriter var2 = GameClient.connection.startPacket();
      PacketTypes.PacketType var3;
      if (this.VehiclePhysicSyncPacketLimit.Check()) {
         var3 = PacketTypes.PacketType.Vehicles;
      } else {
         var3 = PacketTypes.PacketType.VehiclesUnreliable;
      }

      var3.doPacket(var2);
      var2.bb.put((byte)9);
      var2.bb.putShort(var1.VehicleID);
      var2.bb.putLong(GameTime.getServerTime());
      var2.bb.putFloat(var1.physics.EngineForce - var1.physics.BrakingForce);
      if (WorldSimulation.instance.getOwnVehiclePhysics(var1.VehicleID, var2) != 1) {
         GameClient.connection.cancelPacket();
      } else {
         var3.send(GameClient.connection);
      }
   }

   public void sendEngineSound(BaseVehicle var1, float var2, float var3) {
      ByteBufferWriter var4 = GameClient.connection.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var4);
      var4.bb.put((byte)14);
      var4.bb.putShort(var1.VehicleID);
      var4.bb.putFloat(var2);
      var4.bb.putFloat(var3);
      PacketTypes.PacketType.Vehicles.send(GameClient.connection);
   }

   public void sendTowing(UdpConnection var1, BaseVehicle var2, BaseVehicle var3, String var4, String var5, Float var6) {
      ByteBufferWriter var7 = var1.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var7);
      var7.bb.put((byte)17);
      var7.bb.putShort(var2.VehicleID);
      var7.bb.putShort(var3.VehicleID);
      GameWindow.WriteString(var7.bb, var4);
      GameWindow.WriteString(var7.bb, var5);
      PacketTypes.PacketType.Vehicles.send(var1);
   }

   public void sendTowing(BaseVehicle var1, BaseVehicle var2, String var3, String var4, Float var5) {
      if (!this.towedVehicleMap.containsKey(var1.VehicleID)) {
         this.towedVehicleMap.put(var1.VehicleID, var2.VehicleID);
         if (var1.getDriver() != null && var1.getVehicleTowing() != null) {
            var1.getVehicleTowing().setNetPlayerAuthorization((byte)3);
            var1.getVehicleTowing().netPlayerId = var1.getDriver().getOnlineID();
            var1.getVehicleTowing().netPlayerTimeout = 30;
         }

         for(int var6 = 0; var6 < GameServer.udpEngine.connections.size(); ++var6) {
            UdpConnection var7 = (UdpConnection)GameServer.udpEngine.connections.get(var6);
            this.sendTowing(var7, var1, var2, var3, var4, var5);
         }

      }
   }

   public void sendDetachTowing(UdpConnection var1, BaseVehicle var2, BaseVehicle var3) {
      ByteBufferWriter var4 = var1.startPacket();
      PacketTypes.PacketType.Vehicles.doPacket(var4);
      var4.bb.put((byte)18);
      if (var2 != null) {
         var4.bb.put((byte)1);
         var4.bb.putShort(var2.VehicleID);
      } else {
         var4.bb.put((byte)0);
      }

      if (var3 != null) {
         var4.bb.put((byte)1);
         var4.bb.putShort(var3.VehicleID);
      } else {
         var4.bb.put((byte)0);
      }

      PacketTypes.PacketType.Vehicles.send(var1);
   }

   public void sendDetachTowing(BaseVehicle var1, BaseVehicle var2) {
      if (var1 != null) {
         if (this.towedVehicleMap.containsKey(var1.VehicleID)) {
            this.towedVehicleMap.remove(var1.VehicleID);
         }

         if (var1.getDriver() == null) {
            var1.setNetPlayerAuthorization((byte)0);
            var1.netPlayerId = -1;
         } else {
            var1.setNetPlayerAuthorization((byte)3);
            var1.netPlayerId = var1.getDriver().getOnlineID();
            var1.netPlayerTimeout = 30;
         }
      }

      if (var2 != null) {
         if (this.towedVehicleMap.containsKey(var2.VehicleID)) {
            this.towedVehicleMap.remove(var2.VehicleID);
         }

         if (var2.getDriver() == null) {
            var2.setNetPlayerAuthorization((byte)0);
            var2.netPlayerId = -1;
         } else {
            var2.setNetPlayerAuthorization((byte)3);
            var2.netPlayerId = var2.getDriver().getOnlineID();
            var2.netPlayerTimeout = 30;
         }
      }

      for(int var3 = 0; var3 < GameServer.udpEngine.connections.size(); ++var3) {
         UdpConnection var4 = (UdpConnection)GameServer.udpEngine.connections.get(var3);
         this.sendDetachTowing(var4, var1, var2);
      }

   }

   public short getTowedVehicleID(short var1) {
      return this.towedVehicleMap.containsKey(var1) ? this.towedVehicleMap.get(var1) : -1;
   }

   private void writePositionOrientation(ByteBuffer var1, BaseVehicle var2) {
      var1.putLong(WorldSimulation.instance.time);
      Quaternionf var3 = var2.savedRot;
      Transform var4 = var2.getWorldTransform(this.tempTransform);
      var4.getRotation(var3);
      var1.putFloat(var3.x);
      var1.putFloat(var3.y);
      var1.putFloat(var3.z);
      var1.putFloat(var3.w);
      var1.putFloat(var2.netLinearVelocity.x);
      var1.putFloat(var2.netLinearVelocity.y);
      var1.putFloat(var2.netLinearVelocity.z);
      var1.putShort((short)var2.wheelInfo.length);

      for(int var5 = 0; var5 < var2.wheelInfo.length; ++var5) {
         var1.putFloat(var2.wheelInfo[var5].steering);
         var1.putFloat(var2.wheelInfo[var5].rotation);
         var1.putFloat(var2.wheelInfo[var5].skidInfo);
         var1.putFloat(var2.wheelInfo[var5].suspensionLength);
      }

   }

   public static final class PosUpdateVars {
      final Transform transform = new Transform();
      final Vector3f vector3f = new Vector3f();
      final Quaternionf quatf = new Quaternionf();
      final float[] wheelSteer = new float[4];
      final float[] wheelRotation = new float[4];
      final float[] wheelSkidInfo = new float[4];
      final float[] wheelSuspensionLength = new float[4];

      public PosUpdateVars() {
      }
   }

   public static final class VehiclePacket {
      public static final byte PassengerPosition = 1;
      public static final byte Enter = 2;
      public static final byte Exit = 3;
      public static final byte SwitchSeat = 4;
      public static final byte Update = 5;
      public static final byte REnter = 6;
      public static final byte RExit = 7;
      public static final byte Remove = 8;
      public static final byte Physic = 9;
      public static final byte Config = 10;
      public static final byte RequestGetFull = 11;
      public static final byte RequestGetPosition = 12;
      public static final byte AddImpulse = 13;
      public static final byte EngineSound = 14;
      public static final byte Collide = 15;
      public static final byte Sound = 16;
      public static final byte TowingCar = 17;
      public static final byte DetachTowingCar = 18;
      public static final byte InitialWorldState = 19;
      public static final byte Sound_Crash = 1;

      public VehiclePacket() {
      }
   }
}
