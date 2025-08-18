//
//  UIViewController+Extensions.swift
//  RIBs
//
//  Created by Alex Bush on 8/18/25.
//

import UIKit

public extension UIViewController {
    
    /**
     Attaches a RIB to this view controller.
     
     This method triggers the activation of the child's interactor and the loading of its router,
     which is the same underlying action as `Router.attachChild(_ child: Routing)`, with the exception of
     retaining the new child router in an internal list.
     
     This is a helper method for adding RIBs as "leaf" nodes in an existing non-RIBs codebase
     where you cannot refactor the entire view controller hierarchy into RIBs tree. You are responsible for storing a strong
     reference to the attached router and detaching it when it's no longer needed.
     
     - Important: The caller is responsible for retaining the child router. Failure to do so
     will result in the RIB being deallocated immediately.
     
     - Important: Never use this method in a RIBs tree! You should not directly attach RIBs to other RIB's view controllers. Instead use the router to do so. This is only meant as a helper when integrating into a non-RIBs environment.
     
     - Parameter router: Child router to attach.
     */
    func attachRouter(_ router: Routing) {
        router.interactable.activate()
        router.load()
    }

    /**
     Detaches a RIB from this view controller.
     
     This triggers the deactivation of the child's interactor, which is the same underlying
     action as `Router.detachChild(_ child: Routing)`.
     
     - Important: The caller is responsible for releasing their reference to the child router after detaching it.
     
     - Parameter router: Child router to detach.
     */
    func detachRouter(_ router: Routing) {
        router.interactable.deactivate()
    }
}
